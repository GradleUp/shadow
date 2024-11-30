package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import java.io.File
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PropertiesFileTransformerTest : TransformerTestSupport<PropertiesFileTransformer>() {

  @BeforeEach
  fun setUp() {
    transformer = PropertiesFileTransformer(objectFactory)
  }

  @Test
  fun testHasTransformedResource() {
    transformer.transform(TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME)))

    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun testHasNotTransformedResource() {
    assertThat(transformer.hasTransformedResource()).isFalse()
  }

  @Test
  fun testTransformation() {
    transformer.transform(TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME)))

    val testableZipFile = doTransformAndGetTransformedFile(transformer, false)
    val targetLines = readFrom(testableZipFile, MANIFEST_NAME)

    assertThat(targetLines).isNotEmpty()
    assertThat(targetLines).contains("Manifest-Version=1.0")
  }

  @Test
  fun testTransformationPropertiesAreReproducible() {
    transformer.transform(TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME)))

    val firstRunTransformedFile = doTransformAndGetTransformedFile(transformer, true)
    val firstRunTargetLines = readFrom(firstRunTransformedFile, MANIFEST_NAME)

    Thread.sleep(1000) // wait for 1sec to ensure timestamps in properties would change

    val secondRunTransformedFile = doTransformAndGetTransformedFile(transformer, true)
    val secondRunTargetLines = readFrom(secondRunTransformedFile, MANIFEST_NAME)

    assertThat(firstRunTargetLines).isEqualTo(secondRunTargetLines)
  }

  private fun doTransformAndGetTransformedFile(
    transformer: PropertiesFileTransformer,
    preserveFileTimestamps: Boolean,
  ): File {
    val testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
    ZipOutputStream(testableZipFile.outputStream().buffered()).use { zipOutputStream ->
      transformer.modifyOutputStream(zipOutputStream, preserveFileTimestamps)
    }
    return testableZipFile
  }
}
