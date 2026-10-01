package com.github.jengelman.gradle.plugins.shadow.tasks

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.internal.createZipOutputStream
import com.github.jengelman.gradle.plugins.shadow.internal.remapClass
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.containsExactly
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.path.readBytes
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.gradle.api.file.FilePermissions
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFilePermissions
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir

@Suppress("DEPRECATION")
class ShadowCopyActionTest {
  private val classEntry = "${ShadowCopyActionTest::class.java.name.replace('.', '/')}.class"

  @TempDir lateinit var tempDir: File

  @DisabledOnOs(OS.WINDOWS) // TODO: The output jar can't be deleted due to stream closing.
  @Test
  fun throwsZip64RequiredExceptionWhenEntriesExceedLimitWithoutZip64() {
    val action = ShadowCopyAction()
    val stream = CopyActionProcessingStream { streamAction ->
      // Standard ZIP limit is 65535 entries.
      for (i in 0..65535) {
        streamAction.processFile(dummyDetails("file_$i.txt"))
      }
    }

    assertFailure { action.execute(stream) }
      .isInstanceOf<Zip64RequiredException>()
      .hasMessage(
        """
        |archive contains more than 65535 entries.
        |
        |To build this archive, please enable the zip64 extension. e.g.
        |```kts
        |tasks.shadowJar {
        |  isZip64 = true
        |}
        |```
        |See: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Zip.html#org.gradle.api.tasks.bundling.Zip:zip64 for more details.
        """
          .trimMargin()
      )
  }

  @Test
  fun throwsZip64RequiredExceptionWhenNestedInCause() {
    val action = ShadowCopyAction()
    val stream = CopyActionProcessingStream { _ ->
      throw RuntimeException("Wrapping exception", Zip64RequiredException("entry too big"))
    }

    assertFailure { action.execute(stream) }
      .isInstanceOf<Zip64RequiredException>()
      .hasMessage(
        """
        |entry too big
        |
        |To build this archive, please enable the zip64 extension. e.g.
        |```kts
        |tasks.shadowJar {
        |  isZip64 = true
        |}
        |```
        |See: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Zip.html#org.gradle.api.tasks.bundling.Zip:zip64 for more details.
        """
          .trimMargin()
      )
  }

  @Test
  fun succeedsWhenZip64IsEnabledWithManyEntries() {
    val zipFile = tempDir.resolve("output.jar")
    val action = ShadowCopyAction(zipFile = zipFile, isZip64 = true)
    val stream = CopyActionProcessingStream { streamAction ->
      for (i in 0..65535) {
        streamAction.processFile(dummyDetails("file_$i.txt"))
      }
    }

    val result = action.execute(stream)
    assertThat(result.didWork).isTrue()
    JarPath(zipFile.toPath()).use {
      assertThat(it.size()).isEqualTo(65536)
    }
  }

  @Test
  fun remapsManyClassesInParallel() {
    val zipFile = tempDir.resolve("output.jar")
    val action =
      ShadowCopyAction(
        zipFile = zipFile,
        relocators = setOf(SimpleRelocator("com.example", "relocated.example")),
      )

    val rawBytes = requireResourceAsPath(classEntry).readBytes()

    val count = 500
    val stream = CopyActionProcessingStream { streamAction ->
      for (i in 1..count) {
        streamAction.processFile(dummyDetails("com/example/Class$i.class", rawBytes))
      }
    }

    val result = action.execute(stream)
    assertThat(result.didWork).isTrue()
    val expectedEntries =
      (1..count).map { "relocated/example/Class$it.class" } +
        listOf("relocated/example/", "relocated/")
    JarPath(zipFile.toPath()).use {
      assertThat(it).containsExactly(*expectedEntries.toTypedArray())
    }
  }

  @Test
  fun parallelRemappingFasterThanSequential() {
    val relocators = setOf(SimpleRelocator("com.example", "relocated.example"))
    val rawBytes = requireResourceAsPath(classEntry).readBytes()
    val classes = (1..1000).map { "com/example/Class$it.class" to rawBytes }

    fun remapSequential() = classes.map { (path, bytes) ->
      bytes.remapClass(relocators = relocators, path = path)
    }

    fun remapParallel() = runBlocking {
      classes
        .map { (path, bytes) ->
          async(Dispatchers.Default) {
            bytes.remapClass(relocators = relocators, path = path)
          }
        }
        .awaitAll()
    }

    // Warm up JIT and coroutines thread pool
    repeat(3) {
      remapSequential()
      remapParallel()
    }

    var sequentialDuration = Duration.ZERO
    var parallelDuration = Duration.ZERO
    repeat(5) {
      sequentialDuration += measureTime { remapSequential() }
      parallelDuration += measureTime { remapParallel() }
    }

    val ratio = if (Runtime.getRuntime().availableProcessors() == 1) 1.0 else 0.8
    assertThat(parallelDuration).isLessThan(sequentialDuration * ratio)
  }

  private fun ShadowCopyAction(
    zipFile: File = tempDir.resolve("output.jar"),
    isZip64: Boolean = false,
    relocators: Set<Relocator> = emptySet(),
  ) =
    ShadowCopyAction(
      zipFile = zipFile,
      zipOutStream =
        zipFile.createZipOutputStream(
          entryCompression = ZipEntryCompression.STORED,
          isZip64 = isZip64,
          encoding = null,
        ),
      transformers = emptySet(),
      relocators = relocators,
      unusedClasses = emptySet(),
      isPreserveFileTimestamps = true,
      failOnDuplicateEntries = false,
    )
}

private fun dummyDetails(
  path: String,
  bytes: ByteArray = ByteArray(0),
): FileCopyDetailsInternal =
  object : FileCopyDetailsInternal by noOpDelegate() {
    private val _relativePath = RelativePath.parse(true, path)

    override fun isDirectory(): Boolean = false

    override fun getPath(): String = _relativePath.pathString

    override fun getRelativePath(): RelativePath = _relativePath

    override fun open(): InputStream = bytes.inputStream()

    override fun copyTo(target: OutputStream) = target.write(bytes)

    override fun getLastModified(): Long = 0L

    override fun getPermissions(): FilePermissions =
      DefaultFilePermissions(UnixStat.DEFAULT_FILE_PERM)
  }
