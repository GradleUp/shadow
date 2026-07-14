package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.CONSTANT_TIME_FOR_ZIP_ENTRIES
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import java.io.File
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class R8MinimizerTest {
  @TempDir lateinit var tempDir: Path
  private val project = ProjectBuilder.builder().build()
  private val r8Spec = project.objects.newInstance(DefaultR8Spec::class.java)

  @Test
  fun minimizeRequiresR8Classpath() {
    val failure =
      assertThrows<GradleException> {
        minimizer().minimize(tempDir.resolve("input.jar").toFile(), tempDir.toFile())
      }

    assertThat(failure.message)
      .isEqualTo(
        "R8 minimization requires a non-empty R8 classpath. Apply the Shadow plugin or configure the shadowR8 configuration."
      )
  }

  @Test
  fun optimizationRuleReflectsR8Options() {
    val minimizer = minimizer()

    assertThat(minimizer.shouldDisableOptimization(r8Spec.args.get())).isTrue()
    r8Spec.enableOptimization()
    assertThat(minimizer.shouldDisableOptimization(r8Spec.args.get())).isFalse()
  }

  @Test
  fun sourceRulesRelocateFilterDeduplicateAndSortClasses() {
    val source1 = tempDir.resolve("source1").toFile()
    val source2 = tempDir.resolve("source2").toFile()
    source1.resolve("old/B.class").writeBytesCreatingParents()
    source1.resolve("old/A.class").writeBytesCreatingParents()
    source1.resolve("module-info.class").writeBytesCreatingParents()
    source2.resolve("old/A.class").writeBytesCreatingParents()
    source2.resolve("old/Missing.class").writeBytesCreatingParents()
    val input = tempDir.resolve("input.jar").toFile()
    input.writeJar("new/A.class" to byteArrayOf(), "new/B.class" to byteArrayOf())
    val minimizer =
      minimizer(
        sourceDirs = listOf(source1, source2, tempDir.resolve("missing").toFile()),
        relocators = listOf(SimpleRelocator("old", "new")),
      )

    assertThat(minimizer.sourceKeepRules(input))
      .containsExactly(
        "-keep,includedescriptorclasses class new.A { *; }",
        "-keep,includedescriptorclasses class new.B { *; }",
      )
  }

  @Test
  fun dependencyRulesReadDirectoriesAndJarsAndOnlyKeepClassesInInput() {
    val directory = tempDir.resolve("dependency").toFile()
    directory.resolve("dep/B.class").writeBytesCreatingParents()
    directory.resolve("dep/Missing.class").writeBytesCreatingParents()
    val dependencyJar = tempDir.resolve("dependency.jar").toFile()
    dependencyJar.writeJar(
      "dep/A.class" to byteArrayOf(),
      "module-info.class" to byteArrayOf(),
      "resource.txt" to byteArrayOf(),
    )
    val input = tempDir.resolve("input.jar").toFile()
    input.writeJar("dep/A.class" to byteArrayOf(), "dep/B.class" to byteArrayOf())

    assertThat(minimizer(keptFiles = listOf(directory, dependencyJar)).keptDependencyRules(input))
      .containsExactly("-keep class dep.A { *; }", "-keep class dep.B { *; }")
  }

  @Test
  fun serviceRulesIgnoreCommentsMalformedNamesAndDuplicates() {
    val input = tempDir.resolve("services.jar").toFile()
    input.writeJar(
      "META-INF/services/example.Service" to
        "# heading\nexample.Provider # comment\ninvalid-name\nexample.Provider\n\n".toByteArray(),
      "META-INF/services/invalid-name" to "example.Other\n".toByteArray(),
    )

    assertThat(minimizer().serviceKeepRules(input))
      .containsExactly(
        "-keep class example.Service { *; }",
        "-keep class example.Provider { *; }",
        "-keep class example.Other { *; }",
      )
  }

  @Test
  fun createRulesCombinesSourcesInStableOrderAndDeduplicates() {
    val input = tempDir.resolve("input.jar").toFile()
    input.writeJar()
    val extracted =
      tempDir.resolve("extracted.pro").toFile().apply {
        writeText("-keep class extracted.One\n-keep class duplicate.Rule\n")
      }
    val first =
      tempDir.resolve("a.pro").toFile().apply {
        writeText("-keep class duplicate.Rule\n-keep class file.One\n")
      }
    val second = tempDir.resolve("b.pro").toFile().apply { writeText("-keep class file.Two\n") }
    r8Spec.keepRuleFiles.from(second, tempDir.toFile(), first)
    r8Spec.keepRules.addAll("-keep class file.One", "-keep class inline.One")

    assertThat(minimizer().createRules(input, r8Spec.args.get(), extracted))
      .containsExactly(
        DefaultR8Spec.DONT_OPTIMIZE_RULE,
        "-keep class extracted.One",
        "-keep class duplicate.Rule",
        "-keep class file.One",
        "-keep class file.Two",
        "-keep class inline.One",
      )
  }

  @Test
  fun normalizeJarAddsParentsSortsAndNormalizesTimestamps() {
    val input = tempDir.resolve("input.jar").toFile()
    input.writeJar("z/B.txt" to "b".toByteArray(), "a/A.txt" to "a".toByteArray())
    val output = tempDir.resolve("output.jar").toFile()

    minimizer(reproducibleOrder = true, preserveTimestamps = false).normalizeJar(input, output)

    JarFile(output).use { jar ->
      assertThat(jar.entries().toList().map { it.name })
        .containsExactly("a/", "a/A.txt", "z/", "z/B.txt")
      assertThat(jar.entries().toList().map { it.time }.distinct())
        .containsOnly(CONSTANT_TIME_FOR_ZIP_ENTRIES)
      assertThat(jar.getInputStream(jar.getJarEntry("a/A.txt")).readBytes().decodeToString())
        .isEqualTo("a")
    }
  }

  @Test
  fun normalizeJarSupportsStoredEntriesAndPreservedTimes() {
    val input = tempDir.resolve("input.jar").toFile()
    input.writeJar("file.txt" to "content".toByteArray())
    val output = tempDir.resolve("output.jar").toFile()

    minimizer(compression = ZipEntryCompression.STORED, preserveTimestamps = true)
      .normalizeJar(input, output)

    JarFile(output).use { jar ->
      val entry = jar.getJarEntry("file.txt")
      assertThat(entry.method).isEqualTo(ZipOutputStream.STORED)
      assertThat(entry.time >= 0).isTrue()
    }
  }

  private fun minimizer(
    sourceDirs: Iterable<File> = emptyList(),
    keptFiles: Iterable<File> = emptyList(),
    relocators: Iterable<SimpleRelocator> = emptyList(),
    preserveTimestamps: Boolean = false,
    reproducibleOrder: Boolean = true,
    compression: ZipEntryCompression = ZipEntryCompression.DEFLATED,
  ) =
    R8Minimizer(
      execOperations = noOpDelegate<ExecOperations>(),
      logger = Logging.getLogger(R8MinimizerTest::class.java),
      r8Classpath = project.files(),
      r8Spec = r8Spec,
      javaLauncher = project.objects.property(JavaLauncher::class.java),
      sourceSetsClassesDirs = sourceDirs,
      keptDependencyFiles = keptFiles,
      relocators = relocators,
      preserveFileTimestamps = preserveTimestamps,
      reproducibleFileOrder = reproducibleOrder,
      zip64 = false,
      entryCompression = compression,
      metadataCharset = null,
    )

  private fun File.writeBytesCreatingParents() {
    parentFile.mkdirs()
    writeBytes(byteArrayOf())
  }

  private fun File.writeJar(vararg entries: Pair<String, ByteArray>) {
    JarOutputStream(outputStream()).use { output ->
      entries.forEach { (name, bytes) ->
        output.putNextEntry(JarEntry(name))
        output.write(bytes)
        output.closeEntry()
      }
    }
  }
}
