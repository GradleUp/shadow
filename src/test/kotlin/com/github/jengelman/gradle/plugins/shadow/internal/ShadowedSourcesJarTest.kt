package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.containsExactly
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getBytes
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.useAll
import com.github.jengelman.gradle.plugins.shadow.util.testObjectFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ShadowedSourcesJarTest {
  @TempDir lateinit var tempDir: Path

  @Test
  fun metadataCharsetDoesNotChangeSourceEncoding() {
    val srcDir = tempDir.resolve("src").createDirectories()
    val sourceBytes = "class Main { val message = \"你好\" }\n".toByteArray(StandardCharsets.UTF_8)
    srcDir.resolve("Main.kt").writeBytes(sourceBytes)

    val outputJar = tempDir.resolve("output-sources.jar")
    generateSourcesJar(
      sourcesJarFile = outputJar.toFile(),
      sourceSetsSourceDirs = testObjectFactory.fileCollection().from(srcDir),
      metadataCharset = Charsets.US_ASCII.toString(),
    )

    assertThat(JarPath(outputJar)).useAll {
      getBytes("Main.kt").isEqualTo(sourceBytes)
    }
  }

  @Test
  fun isUnusedMatching() {
    val unusedSet =
      setOf(
        "com.example.UnusedJava",
        $$"com.example.UnusedJava$Inner",
        "com.example.UnusedKtClass",
        "com.example.DefaultFacadeKt",
        "com.example.CustomFacade",
      )
    val sourceToClasses =
      mapOf(
        "com/example/UnusedJava.java" to
          setOf("com.example.UnusedJava", $$"com.example.UnusedJava$Inner"),
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
  fun deterministicOrdering() {
    val srcDir = tempDir.resolve("src").createDirectories()
    srcDir.resolve("z/sub/Z.java").apply {
      createParentDirectories()
      writeText("package z.sub;\nclass Z {}")
    }
    srcDir.resolve("a/A.java").apply {
      createParentDirectories()
      writeText("package a;\nclass A {}")
    }
    srcDir.resolve("m/M.java").apply {
      createParentDirectories()
      writeText("package m;\nclass M {}")
    }

    val outputJar = tempDir.resolve("output-sources.jar")
    generateSourcesJar(
      sourcesJarFile = outputJar.toFile(),
      sourceSetsSourceDirs = testObjectFactory.fileCollection().from(srcDir),
    )

    assertThat(JarPath(outputJar)).useAll {
      containsExactly(
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
  }

  @Test
  fun respectsExcludedDirectory() {
    val srcDir = tempDir.resolve("src").createDirectories()
    srcDir.resolve("Excluded.java").writeText("public class Excluded {}")

    val fileTree =
      testObjectFactory.fileCollection().from(srcDir).asFileTree.matching {
        it.exclude("**/Excluded.java")
      }

    val outputJar = tempDir.resolve("output-sources.jar")
    generateSourcesJar(
      sourcesJarFile = outputJar.toFile(),
      sourceSetsSourceDirs = testObjectFactory.fileCollection().from(fileTree),
    )

    assertThat(JarPath(outputJar)).useAll {
      containsOnly("META-INF/", "META-INF/MANIFEST.MF")
    }
  }

  @Test
  fun packageDeclarationFollowsClassIncludesAndExcludes() {
    val srcDir = tempDir.resolve("src").createDirectories()
    srcDir.resolve("org/foo/Bar.java").apply {
      createParentDirectories()
      writeText("package org.foo;\npublic class Bar {}")
    }
    srcDir.resolve("org/foo/Baz.java").apply {
      createParentDirectories()
      writeText("package org.foo;\nimport org.foo.Bar;\npublic class Baz {}")
    }
    // Not laid out in its package directory, so its package declaration is left as-is.
    srcDir.resolve("Flat.kt").writeText("package org.foo\nclass Flat")

    val excludeJar = tempDir.resolve("exclude-sources.jar")
    generateSourcesJar(
      sourcesJarFile = excludeJar.toFile(),
      sourceSetsSourceDirs = testObjectFactory.fileCollection().from(srcDir),
      relocators =
        listOf(SimpleRelocator("org.foo", "shaded.org.foo", excludes = listOf("org.foo.Bar"))),
    )
    assertThat(JarPath(excludeJar)).useAll {
      getContent("org/foo/Bar.java").isEqualTo("package org.foo;\npublic class Bar {}")
      getContent("shaded/org/foo/Baz.java")
        .isEqualTo("package shaded.org.foo;\nimport org.foo.Bar;\npublic class Baz {}")
      getContent("Flat.kt").isEqualTo("package shaded.org.foo\nclass Flat")
    }

    val includeJar = tempDir.resolve("include-sources.jar")
    generateSourcesJar(
      sourcesJarFile = includeJar.toFile(),
      sourceSetsSourceDirs = testObjectFactory.fileCollection().from(srcDir),
      relocators =
        listOf(SimpleRelocator("org.foo", "shaded.org.foo", includes = listOf("org.foo.Bar"))),
    )
    assertThat(JarPath(includeJar)).useAll {
      getContent("shaded/org/foo/Bar.java")
        .isEqualTo("package shaded.org.foo;\npublic class Bar {}")
      getContent("org/foo/Baz.java")
        .isEqualTo("package org.foo;\nimport shaded.org.foo.Bar;\npublic class Baz {}")
      getContent("Flat.kt").isEqualTo("package org.foo\nclass Flat")
    }
  }

  @Test
  fun excludesModuleInfo() {
    val srcDir = tempDir.resolve("src").createDirectories()
    srcDir.resolve("module-info.java").writeText("module my.module {}")
    srcDir.resolve("com/example/Main.java").apply {
      createParentDirectories()
      writeText("package com.example;\npublic class Main {}")
    }

    val depSourcesJar = tempDir.resolve("dep-sources.jar")
    ZipOutputStream(depSourcesJar.toFile()).use { zos ->
      zos.writeEntry("module-info.java") { write("module dep {}".toByteArray()) }
      zos.writeEntry("jvmMain/module-info.java") { write("module dep.jvm {}".toByteArray()) }
      zos.writeEntry("dep/Dep.java") {
        write("package dep;\npublic class Dep {}".toByteArray())
      }
    }

    val outputJar = tempDir.resolve("output-sources.jar")
    generateSourcesJar(
      sourcesJarFile = outputJar.toFile(),
      sourceSetsSourceDirs = testObjectFactory.fileCollection().from(srcDir),
      includedSourcesJars = listOf(depSourcesJar.toFile()),
    )

    assertThat(JarPath(outputJar)).useAll {
      containsOnly(
        "META-INF/MANIFEST.MF",
        "com/example/Main.java",
        "dep/Dep.java",
        "META-INF/",
        "com/",
        "com/example/",
        "dep/",
      )
    }
  }

  @Test
  fun mergesAndRelocatesIncludedSourcesJar() {
    val srcDir = tempDir.resolve("src").createDirectories()
    srcDir.resolve("com/example/Main.java").apply {
      createParentDirectories()
      writeText("package com.example;\npublic class Main {}")
    }

    val depSourcesJar = tempDir.resolve("dep-sources.jar")
    depSourcesJar
      .toFile()
      .createZipOutputStream(
        entryCompression = ZipEntryCompression.STORED,
        isZip64 = false,
        encoding = null,
      )
      .use { zos ->
        zos.writeEntry("dep/Dep.java") {
          write("package dep;\npublic class Dep {}".toByteArray())
        }
        zos.writeEntry("dep/resource.txt") {
          write("dep resource".toByteArray())
        }
      }

    val outputJar = tempDir.resolve("output-sources.jar")
    generateSourcesJar(
      sourcesJarFile = outputJar.toFile(),
      sourceSetsSourceDirs = testObjectFactory.fileCollection().from(srcDir),
      includedSourcesJars = listOf(depSourcesJar.toFile()),
      relocators = listOf(SimpleRelocator("dep", "shaded.dep")),
    )

    assertThat(JarPath(outputJar)).useAll {
      containsExactly(
        "META-INF/MANIFEST.MF",
        "com/example/Main.java",
        "shaded/dep/Dep.java",
        "shaded/dep/resource.txt",
        "META-INF/",
        "com/",
        "com/example/",
        "shaded/",
        "shaded/dep/",
      )
      getContent("shaded/dep/Dep.java").isEqualTo("package shaded.dep;\npublic class Dep {}")
      getContent("shaded/dep/resource.txt").isEqualTo("dep resource")
    }
  }
}

private fun generateSourcesJar(
  sourcesJarFile: File,
  sourceSetsSourceDirs: FileCollection,
  includedSourcesJars: Iterable<File> = emptyList(),
  relocators: Iterable<Relocator> = emptyList(),
  metadataCharset: String? = null,
) =
  generateSourcesJar(
    sourcesJarFile = sourcesJarFile,
    zipOutStream =
      sourcesJarFile.createZipOutputStream(
        entryCompression = ZipEntryCompression.STORED,
        isZip64 = false,
        encoding = metadataCharset,
      ),
    sourceSetsSourceDirs = sourceSetsSourceDirs,
    includedSourcesJars = includedSourcesJars,
    classesDirs = emptyList(),
    dependencies = emptyList(),
    relocators = relocators,
    unusedClasses = emptySet(),
    preserveFileTimestamps = true,
  )
