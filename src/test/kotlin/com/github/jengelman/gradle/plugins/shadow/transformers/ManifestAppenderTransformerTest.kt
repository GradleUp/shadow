package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import java.util.jar.JarFile.MANIFEST_NAME
import kotlin.io.path.readLines
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

    val targetLines = doTransformAndGetTransformedPath(transformer, true)
      .getContent(MANIFEST_NAME).lines()
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
  fun noTransformation() {
    val sourceLines = requireResourceAsPath(MANIFEST_NAME).readLines()
    transformer.transform(manifestTransformerContext)
    val targetLines = doTransformAndGetTransformedPath(transformer, true)
      .getContent(MANIFEST_NAME).lines()

    assertThat(targetLines).isEqualTo(sourceLines)
  }
}
