package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.hasMessage
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.util.testObjectFactory
import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SourcesJarTest {

  @Test
  fun isUnusedMatching() {
    val unusedSet =
      setOf(
        "com.example.UnusedJava",
        "com.example.UnusedJava\$Inner",
        "com.example.UnusedKtClass",
        "com.example.DefaultFacadeKt",
        "com.example.CustomFacade",
      )
    val sourceToClasses =
      mapOf(
        "com/example/UnusedJava.java" to
          setOf("com.example.UnusedJava", "com.example.UnusedJava\$Inner"),
        "com/example/PartiallyUsedJava.java" to
          setOf("com.example.UnusedJava", "com.example.UsedHelper"),
        "com/example/UsedJava.java" to setOf("com.example.UsedJava"),
        "com/example/UnusedKtClass.kt" to setOf("com.example.UnusedKtClass"),
        "com/example/DefaultFacade.kt" to setOf("com.example.DefaultFacadeKt"),
        "com/example/Utils.kt" to setOf("com.example.CustomFacade"),
        "com/example/MixedUtils.kt" to setOf("com.example.CustomFacade", "com.example.UsedClass"),
        "Main.java" to setOf("Main"),
      )

    // All classes unused in file -> unused
    assertThat(isUnused("com/example/UnusedJava.java", unusedSet, sourceToClasses)).isTrue()
    assertThat(isUnused("com/example/UnusedKtClass.kt", unusedSet, sourceToClasses)).isTrue()
    assertThat(isUnused("com/example/DefaultFacade.kt", unusedSet, sourceToClasses)).isTrue()
    assertThat(isUnused("com/example/Utils.kt", unusedSet, sourceToClasses)).isTrue()
    assertThat(isUnused("Main.java", setOf("Main"), sourceToClasses)).isTrue()

    // At least one class is used in file -> NOT unused (kept!)
    assertThat(isUnused("com/example/PartiallyUsedJava.java", unusedSet, sourceToClasses)).isFalse()
    assertThat(isUnused("com/example/MixedUtils.kt", unusedSet, sourceToClasses)).isFalse()
    assertThat(isUnused("com/example/UsedJava.java", unusedSet, sourceToClasses)).isFalse()

    // Unknown source file or empty unused set -> kept
    assertThat(isUnused("com/example/Unknown.java", unusedSet, sourceToClasses)).isFalse()
    assertThat(isUnused("com/example/UnusedJava.java", emptySet(), sourceToClasses)).isFalse()
    assertThat(isUnused("Main.java", setOf("Other"), sourceToClasses)).isFalse()
  }

  @Test
  fun deterministicOrdering(@TempDir tempDir: File) {
    val srcDir = tempDir.resolve("src").apply { mkdirs() }
    srcDir.resolve("z/sub/Z.java").apply {
      parentFile.mkdirs()
      writeText("package z.sub;\nclass Z {}")
    }
    srcDir.resolve("a/A.java").apply {
      parentFile.mkdirs()
      writeText("package a;\nclass A {}")
    }
    srcDir.resolve("m/M.java").apply {
      parentFile.mkdirs()
      writeText("package m;\nclass M {}")
    }

    val outputJar = tempDir.resolve("output-sources.jar")
    generateSourcesJar(
      sourcesJarFile = outputJar,
      sourceSetsSourceDirs = testObjectFactory.fileCollection().from(srcDir),
      includedSourcesJars = emptyList(),
      relocators = emptyList(),
      unusedClasses = emptySet(),
      entryCompression = ZipEntryCompression.DEFLATED,
      isZip64 = false,
      metadataCharset = null,
      preserveFileTimestamps = true,
    )

    val entries = ZipFile(outputJar).use { zip -> zip.entries().toList().map { it.name } }
    assertThat(entries)
      .containsExactly(
        "META-INF/MANIFEST.MF",
        "a/A.java",
        "m/M.java",
        "z/sub/Z.java",
        "META-INF/",
        "a/",
        "m/",
        "z/",
        "z/sub/",
      )
  }

  @Test
  fun respectsExcludedDirectory(@TempDir tempDir: File) {
    val srcDir = tempDir.resolve("src").apply { mkdirs() }
    srcDir.resolve("Excluded.java").writeText("public class Excluded {}")

    val fileTree =
      testObjectFactory.fileCollection().from(srcDir).asFileTree.matching {
        it.exclude("**/Excluded.java")
      }

    val outputJar = tempDir.resolve("output-sources.jar")
    generateSourcesJar(
      sourcesJarFile = outputJar,
      sourceSetsSourceDirs = testObjectFactory.fileCollection().from(fileTree),
      includedSourcesJars = emptyList(),
      relocators = emptyList(),
      unusedClasses = emptySet(),
      entryCompression = ZipEntryCompression.DEFLATED,
      isZip64 = false,
      metadataCharset = null,
      preserveFileTimestamps = true,
    )

    val entries = ZipFile(outputJar).use { zip -> zip.entries().toList().map { it.name } }
    assertThat(entries).containsOnly("META-INF/", "META-INF/MANIFEST.MF")
  }

  @Test
  fun throwsGradleExceptionOnFailure(@TempDir tempDir: File) {
    val invalidFile = tempDir.resolve("not-a-file").apply { mkdirs() }
    val srcDir =
      tempDir.resolve("src").apply {
        mkdirs()
        resolve("Main.java").writeText("public class Main {}")
      }

    assertFailure {
        generateSourcesJar(
          sourcesJarFile = invalidFile,
          sourceSetsSourceDirs = testObjectFactory.fileCollection().from(srcDir),
          includedSourcesJars = emptyList(),
          relocators = emptyList(),
          unusedClasses = emptySet(),
          entryCompression = ZipEntryCompression.DEFLATED,
          isZip64 = false,
          metadataCharset = null,
          preserveFileTimestamps = true,
        )
      }
      .isInstanceOf<GradleException>()
      .hasMessage("Could not create shadowed sources JAR '$invalidFile'.")
  }
}
