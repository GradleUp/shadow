package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import de.infix.testBalloon.framework.core.testSuite

val PreserveFirstFoundResourceTransformerTests by testSuite {
  runTests(::PreserveFirstFoundResourceTransformerTest)
}

private class PreserveFirstFoundResourceTransformerTest :
  BaseTransformerTest<PreserveFirstFoundResourceTransformer>() {

  fun firstOccurrenceIsNotTransformed() =
    with(transformer) {
      include("foo/bar")

      // First occurrence: not yet found → canTransformResource returns false (do not intercept)
      assertThat(canTransformResource("foo/bar")).isFalse()
    }

  fun subsequentOccurrencesAreTransformed() =
    with(transformer) {
      include("foo/bar")

      canTransformResource("foo/bar") // first call — registers it

      // Second occurrence: already found → canTransformResource returns true (intercept/drop)
      assertThat(canTransformResource("foo/bar")).isTrue()
    }

  fun nonMatchingPathIsNotTransformed() =
    with(transformer) {
      include("foo/bar")

      assertThat(canTransformResource("foo/baz")).isFalse()
    }
}
