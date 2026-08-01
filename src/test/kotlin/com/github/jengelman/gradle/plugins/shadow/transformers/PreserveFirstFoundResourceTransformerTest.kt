package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class PreserveFirstFoundResourceTransformerTest :
  BaseTransformerTest<PreserveFirstFoundResourceTransformer>() {

  @Test
  fun firstOccurrenceIsNotTransformed() =
    with(transformer) {
      include("foo/bar")

      // First occurrence: not yet found → canTransformResource returns false (do not intercept)
      assertThat(canTransformResource("foo/bar")).isFalse()
    }

  @Test
  fun subsequentOccurrencesAreTransformed() =
    with(transformer) {
      include("foo/bar")

      canTransformResource("foo/bar") // first call — registers it

      // Second occurrence: already found → canTransformResource returns true (intercept/drop)
      assertThat(canTransformResource("foo/bar")).isTrue()
    }

  @Test
  fun nonMatchingPathIsNotTransformed() =
    with(transformer) {
      include("foo/bar")

      assertThat(canTransformResource("foo/baz")).isFalse()
    }
}
