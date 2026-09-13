package com.github.jengelman.gradle.plugins.shadow.tasks

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.internal.createZipOutputStream
import com.github.jengelman.gradle.plugins.shadow.internal.useZip
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.gradle.api.file.FilePermissions
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFilePermissions
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@Suppress("DEPRECATION")
class ShadowCopyActionTest {
  @TempDir lateinit var tempDir: File

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
    zipFile.useZip { assertThat(size()).isEqualTo(65536) }
  }

  private fun ShadowCopyAction(
    zipFile: File = tempDir.resolve("output.jar"),
    isZip64: Boolean = false,
  ) =
    ShadowCopyAction(
      zipFile = zipFile,
      zipOutStream =
        zipFile.createZipOutputStream(
          entryCompression = ZipEntryCompression.DEFLATED,
          isZip64 = isZip64,
          encoding = null,
        ),
      transformers = emptySet(),
      relocators = emptySet(),
      unusedClasses = emptySet(),
      isPreserveFileTimestamps = true,
      failOnDuplicateEntries = false,
    )
}

private fun dummyDetails(path: String): FileCopyDetailsInternal =
  object : FileCopyDetailsInternal by noOpDelegate() {
    private val _relativePath = RelativePath.parse(true, path)

    override fun isDirectory(): Boolean = false

    override fun getPath(): String = _relativePath.pathString

    override fun getRelativePath(): RelativePath = _relativePath

    override fun open(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun copyTo(target: OutputStream) = Unit

    override fun getLastModified(): Long = 0L

    override fun getPermissions(): FilePermissions =
      DefaultFilePermissions(UnixStat.DEFAULT_FILE_PERM)
  }
