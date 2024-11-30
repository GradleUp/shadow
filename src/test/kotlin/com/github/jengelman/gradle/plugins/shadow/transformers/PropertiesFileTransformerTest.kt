package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PropertiesFileTransformerTest : TransformerTestSupport<PropertiesFileTransformer>() {

  @BeforeEach
  fun setup() {
    transformer = PropertiesFileTransformer(objectFactory)
  }

  @Test
  fun testHasTransformedResource() {
    transformer.transform(manifestTransformerContext)

    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun testHasNotTransformedResource() {
    assertThat(transformer.hasTransformedResource()).isFalse()
  }

  @Test
  fun testTransformation() {
    transformer.transform(manifestTransformerContext)

    val testableZipPath = doTransformAndGetTransformedPath(transformer, false)
    val targetLines = readFrom(testableZipPath, MANIFEST_NAME)

    assertThat(targetLines).isNotEmpty()
    assertThat(targetLines).contains("Manifest-Version=1.0")
  }

  @Test
  fun testTransformationPropertiesAreReproducible() {
    transformer.transform(manifestTransformerContext)

    val firstRunTransformedPath = doTransformAndGetTransformedPath(transformer, true)
    val firstRunTargetLines = readFrom(firstRunTransformedPath, MANIFEST_NAME)

    Thread.sleep(1000) // wait for 1sec to ensure timestamps in properties would change

    val secondRunTransformedPath = doTransformAndGetTransformedPath(transformer, true)
    val secondRunTargetLines = readFrom(secondRunTransformedPath, MANIFEST_NAME)

    assertThat(firstRunTargetLines).isEqualTo(secondRunTargetLines)
  }
}
