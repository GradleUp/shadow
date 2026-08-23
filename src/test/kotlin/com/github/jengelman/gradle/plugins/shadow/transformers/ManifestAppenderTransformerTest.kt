package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.crlfEolString
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import java.util.jar.JarFile.MANIFEST_NAME
import org.junit.jupiter.api.Test

class ManifestAppenderTransformerTest : BaseTransformerTest<ManifestAppenderTransformer>() {
  @Test
  fun canTransformResource() =
    with(transformer) {
      append("Name", "org/foo/bar/")
      append("Sealed", true)

      assertThat(canTransformResource(MANIFEST_NAME)).isTrue()
      assertThat(canTransformResource(MANIFEST_NAME.lowercase())).isTrue()
    }

  @Test
  fun hasTransformedResource() =
    with(transformer) {
      assertThat(transformer.hasTransformedResource()).isFalse()

      append("Tag", "Something")

      assertThat(hasTransformedResource()).isTrue()
    }

  @Test
  fun transformation() =
    with(transformer) {
      append("Name", "org/foo/bar/")
      append("Sealed", true)
      append("Name", "com/example/")
      append("Sealed", false)

      val source = "Manifest-Version: 1.0\r\n\r\n"
      transform(textContext(MANIFEST_NAME, source))

      val target = transformToJar().use { it.getContent(MANIFEST_NAME) }
      assertThat(target)
        .isEqualTo(
          """
          |Manifest-Version: 1.0
          |
          |Name: org/foo/bar/
          |Sealed: true
          |Name: com/example/
          |Sealed: false
          |
          |"""
            .trimMargin()
            .crlfEolString
        )
    }

  @Test
  fun noTransformation() =
    with(transformer) {
      val source = "Manifest-Version: 1.0\r\n"
      transform(textContext(MANIFEST_NAME, source))
      val target = transformToJar().use { it.getContent(MANIFEST_NAME) }

      assertThat(target).isEqualTo(source)
    }
}
