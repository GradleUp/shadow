package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SourcesJarTest {

  @Test
  fun extractPackageStatements() {
    assertThat(extractPackage("package com.example.foo;")).isEqualTo("com.example.foo")
    assertThat(extractPackage("package com.example.foo")).isEqualTo("com.example.foo")
    assertThat(extractPackage("  package   com.example.foo.bar  ; "))
      .isEqualTo("com.example.foo.bar")
    assertThat(
        extractPackage(
          """
          /*
           * Multi-line header comment.
           */
          package com.example.license;
          public class License {}
          """
            .trimIndent()
        )
      )
      .isEqualTo("com.example.license")
    assertThat(
        extractPackage(
          """
          @file:JvmName("MyUtils")
          package com.example.annotated
          fun test() {}
          """
            .trimIndent()
        )
      )
      .isEqualTo("com.example.annotated")
    assertThat(
        extractPackage(
          """
          package a
          package b.c
          class Chained
          """
            .trimIndent()
        )
      )
      .isEqualTo("a.b.c")
    assertThat(extractPackage("public class NoPackage {}")).isEqualTo("")
  }

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
  fun generateShadowedSourcesJarNormalizesPackageDirectory(@TempDir tempDir: File) {
    val srcDir = tempDir.resolve("src").apply { mkdirs() }
    val flatMismatchedFile = srcDir.resolve("Mismatched.kt")
    flatMismatchedFile.writeText(
      """
      package com.example.nested
      class Mismatched
      """
        .trimIndent()
    )

    val outputJar = tempDir.resolve("output-sources.jar")
    generateShadowedSourcesJar(
      sourcesJarFile = outputJar,
      sourceSetsSourceDirs = listOf(srcDir),
      includedSourcesJars = emptyList(),
      relocators = listOf(SimpleRelocator("com.example", "shadow.example")),
      unusedClasses = emptySet(),
      entryCompression = ZipEntryCompression.DEFLATED,
      isZip64 = false,
      metadataCharset = null,
      preserveFileTimestamps = true,
    )

    assertThat(outputJar.exists()).isTrue()
    val entries = ZipFile(outputJar).use { zip -> zip.entries().toList().map { it.name } }
    assertThat(entries)
      .containsOnly(
        "META-INF/",
        "META-INF/MANIFEST.MF",
        "shadow/",
        "shadow/example/",
        "shadow/example/nested/",
        "shadow/example/nested/Mismatched.kt",
      )
  }

  @Test
  fun generateShadowedSourcesJarDeterministicOrdering(@TempDir tempDir: File) {
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
    generateShadowedSourcesJar(
      sourcesJarFile = outputJar,
      sourceSetsSourceDirs = listOf(srcDir),
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
      .isEqualTo(
        listOf(
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
      )
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
        generateShadowedSourcesJar(
          sourcesJarFile = invalidFile,
          sourceSetsSourceDirs = listOf(srcDir),
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
