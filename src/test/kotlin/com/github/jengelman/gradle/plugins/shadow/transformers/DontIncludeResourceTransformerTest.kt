package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class DontIncludeResourceTransformerTest : BaseTransformerTest<DontIncludeResourceTransformer>() {
  @Test
  fun canTransformResource() =
    with(transformer) {
      resource.set("foo")

      assertThat(canTransformResource("foo")).isTrue()
      assertThat(canTransformResource("bar/foo")).isTrue()
      assertThat(canTransformResource("bar")).isFalse()
    }

  @Test
  fun canNotTransformWithEmptyResource() =
    with(transformer) {
      resource.set("")

      assertThat(canTransformResource("foo")).isFalse()
    }
}
