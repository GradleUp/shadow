package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class ManifestAppenderTransformerTest : BaseTransformerTest<ManifestAppenderTransformer>() {
  @Test
  fun testCanTransformResource() {
    with(transformer) {
      append("Name", "org/foo/bar/")
      append("Sealed", true)
    }

    assertThat(transformer.canTransformResource(getFileElement(MANIFEST_NAME))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement(MANIFEST_NAME.lowercase()))).isTrue()
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

      transform(manifestTransformerContext)
    }

    val testableZipPath = doTransformAndGetTransformedPath(transformer, true)

    val targetLines = readFrom(testableZipPath)
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
    transformer.transform(manifestTransformerContext)
    val testableZipPath = doTransformAndGetTransformedPath(transformer, true)
    val targetLines = readFrom(testableZipPath)

    assertThat(targetLines).isEqualTo(sourceLines)
  }
}
