package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsAtLeast
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
        "com.example.UnusedKtClass",
        "com.example.DefaultFacadeKt",
        "com.example.CustomFacade",
      )

    assertThat(isUnused("UnusedJava.java", "com.example", "class UnusedJava {}", unusedSet))
      .isTrue()
    assertThat(isUnused("UsedJava.java", "com.example", "class UsedJava {}", unusedSet)).isFalse()

    assertThat(isUnused("UnusedKtClass.kt", "com.example", "class UnusedKtClass", unusedSet))
      .isTrue()
    assertThat(
        isUnused(
          "DefaultFacade.kt",
          "com.example",
          "fun topLevel() {}",
          unusedSet,
        )
      )
      .isTrue()
    assertThat(
        isUnused(
          "Utils.kt",
          "com.example",
          """
          @file:JvmName("CustomFacade")
          package com.example
          fun util() {}
          """
            .trimIndent(),
          unusedSet,
        )
      )
      .isTrue()
    assertThat(
        isUnused(
          "Utils.kt",
          "com.example",
          """
          @file:kotlin.jvm.JvmName(name = "CustomFacade")
          package com.example
          fun util() {}
          """
            .trimIndent(),
          unusedSet,
        )
      )
      .isTrue()
    assertThat(
        isUnused(
          "UsedUtils.kt",
          "com.example",
          """
          @file:JvmName("UsedFacade")
          package com.example
          fun util() {}
          """
            .trimIndent(),
          unusedSet,
        )
      )
      .isFalse()
    assertThat(
        isUnused(
          "BracketedUtils.kt",
          "com.example",
          """
          @file:[JvmName("CustomFacade")]
          package com.example
          fun util() {}
          """
            .trimIndent(),
          unusedSet,
        )
      )
      .isTrue()
    assertThat(
        isUnused(
          "BracketedMultiUtils.kt",
          "com.example",
          """
          @file:[Suppress("unused") JvmName("CustomFacade")]
          package com.example
          fun util() {}
          """
            .trimIndent(),
          unusedSet,
        )
      )
      .isTrue()
    assertThat(
        isUnused(
          "BracketedMultiUtilsReversed.kt",
          "com.example",
          """
          @file:[JvmName("CustomFacade") Suppress("unused")]
          package com.example
          fun util() {}
          """
            .trimIndent(),
          unusedSet,
        )
      )
      .isTrue()

    assertThat(isUnused("UnusedJava.java", "com.example", "class UnusedJava {}", emptySet()))
      .isFalse()
    assertThat(isUnused("Main.java", "", "class Main {}", setOf("Main"))).isTrue()
    assertThat(isUnused("Main.java", "", "class Main {}", setOf("Other"))).isFalse()
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
    assertThat(entries).containsAtLeast("shadow/example/nested/Mismatched.kt")
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
