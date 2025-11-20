package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getStream
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsStream
import java.util.jar.JarFile.MANIFEST_NAME
import org.junit.jupiter.api.Test

class ManifestAppenderTransformerTest : BaseTransformerTest<ManifestAppenderTransformer>() {
  @Test
  fun canTransformResource() {
    with(transformer) {
      append("Name", "org/foo/bar/")
      append("Sealed", true)
    }

    assertThat(transformer.canTransformResource(MANIFEST_NAME)).isTrue()
    assertThat(transformer.canTransformResource(MANIFEST_NAME.lowercase())).isTrue()
  }

  @Test
  fun hasTransformedResource() {
    transformer.append("Tag", "Something")

    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun hasNotTransformedResource() {
    assertThat(transformer.hasTransformedResource()).isFalse()
  }

  @Test
  fun transformation() {
    with(transformer) {
      append("Name", "org/foo/bar/")
      append("Sealed", true)
      append("Name", "com/example/")
      append("Sealed", false)

      transform(manifestTransformerContext)
    }

    val targetLines = transformer.transformToJar().use { it.getContent(MANIFEST_NAME).trim().lines() }
    assertThat(targetLines.size).isGreaterThan(5)
    assertThat(targetLines.takeLast(4)).isEqualTo(
      listOf(
        "Name: org/foo/bar/",
        "Sealed: true",
        "Name: com/example/",
        "Sealed: false",
      ),
    )
  }

  @Test
  fun noTransformation() {
    val sourceLines = requireResourceAsStream(MANIFEST_NAME).reader().readLines()
    transformer.transform(manifestTransformerContext)
    val targetLines = transformer.transformToJar().use { it.getStream(MANIFEST_NAME).reader().readLines() }

    assertThat(targetLines).isEqualTo(sourceLines)
  }
}
