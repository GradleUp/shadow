package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getStream
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsStream
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import de.infix.testBalloon.framework.core.testSuite
import java.util.jar.JarFile.MANIFEST_NAME

val ManifestAppenderTransformerTests by testSuite {
  runTests(::ManifestAppenderTransformerTest)
}

private class ManifestAppenderTransformerTest : BaseTransformerTest<ManifestAppenderTransformer>() {
  fun canTransformResource() =
    with(transformer) {
      append("Name", "org/foo/bar/")
      append("Sealed", true)

      assertThat(canTransformResource(MANIFEST_NAME)).isTrue()
      assertThat(canTransformResource(MANIFEST_NAME.lowercase())).isTrue()
    }

  fun hasTransformedResource() =
    with(transformer) {
      assertThat(transformer.hasTransformedResource()).isFalse()

      append("Tag", "Something")

      assertThat(hasTransformedResource()).isTrue()
    }

  fun transformation() =
    with(transformer) {
      append("Name", "org/foo/bar/")
      append("Sealed", true)
      append("Name", "com/example/")
      append("Sealed", false)

      transform(manifestTransformerContext)

      val targetLines = transformToJar().use { it.getContent(MANIFEST_NAME).trim().lines() }
      assertThat(targetLines.size).isGreaterThanOrEqualTo(4)
      assertThat(targetLines.takeLast(4))
        .isEqualTo(
          listOf("Name: org/foo/bar/", "Sealed: true", "Name: com/example/", "Sealed: false")
        )
    }

  fun noTransformation() =
    with(transformer) {
      val sourceLines = requireResourceAsStream(MANIFEST_NAME).reader().readLines()
      transform(manifestTransformerContext)
      val targetLines = transformToJar().use { it.getStream(MANIFEST_NAME).reader().readLines() }

      assertThat(targetLines).isEqualTo(sourceLines)
    }
}
