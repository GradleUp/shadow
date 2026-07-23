package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class XmlAppendingTransformerTest : BaseTransformerTest<XmlAppendingTransformer>() {

  init {
    setupTurkishLocale()
  }

  @Test
  fun canTransformResource() =
    with(transformer) {
      resource.set("abcdefghijklmnopqrstuvwxyz")

      assertThat(canTransformResource("abcdefghijklmnopqrstuvwxyz")).isTrue()
      assertThat(canTransformResource("ABCDEFGHIJKLMNOPQRSTUVWXYZ")).isTrue()
      assertThat(canTransformResource("META-INF/MANIFEST.MF")).isFalse()
    }
}
