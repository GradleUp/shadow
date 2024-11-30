package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ManifestAppenderTransformerTest : TransformerTestSupport<ManifestAppenderTransformer>() {
  @BeforeEach
  fun setup() {
    transformer = ManifestAppenderTransformer(objectFactory)
  }

  @Test
  fun testCanTransformResource() {
    with(transformer) {
      append("Name", "org/foo/bar/")
      append("Sealed", true)
    }

    assertThat(transformer.canTransformResource(getFileElement(MANIFEST_NAME))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement(MANIFEST_NAME.toLowerCase()))).isTrue()
  }

  @Test
  fun testHasTransformedResource() {
    transformer.append("Tag", "Something")

    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun testHasNotTransformedResource() {
    assertThat(transformer.hasTransformedResource()).isFalse()
  }

  @Test
  fun testTransformation() {
    with(transformer) {
      append("Name", "org/foo/bar/")
      append("Sealed", true)
      append("Name", "com/example/")
      append("Sealed", false)

      transform(TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME)))
    }

    val testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
    val fileOutputStream = FileOutputStream(testableZipFile)
    val bufferedOutputStream = BufferedOutputStream(fileOutputStream)

    ZipOutputStream(bufferedOutputStream).use { zipOutputStream ->
      transformer.modifyOutputStream(zipOutputStream, true)
    }

    val targetLines = readFrom(testableZipFile)
    assertThat(targetLines).isNotEmpty()
    assertThat(targetLines.size).isGreaterThan(4)

    val trailer = targetLines.subList(targetLines.size - 5, targetLines.size)
    assertThat(trailer).isEqualTo(
      listOf(
        "Name: org/foo/bar/",
        "Sealed: true",
        "Name: com/example/",
        "Sealed: false",
        "",
      ),
    )
  }

  @Test
  fun testNoTransformation() {
    val sourceLines = requireResourceAsStream(MANIFEST_NAME).bufferedReader().readLines()
    transformer.transform(TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME)))
    val testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
    ZipOutputStream(testableZipFile.outputStream().buffered()).use { zipOutputStream ->
      transformer.modifyOutputStream(zipOutputStream, true)
    }
    val targetLines = readFrom(testableZipFile)

    assertThat(targetLines).isEqualTo(sourceLines)
  }
}
