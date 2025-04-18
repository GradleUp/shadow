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
  fun canTransformResource() {
    transformer.resource.set("abcdefghijklmnopqrstuvwxyz")

    assertThat(transformer.canTransformResource("abcdefghijklmnopqrstuvwxyz")).isTrue()
    assertThat(transformer.canTransformResource("ABCDEFGHIJKLMNOPQRSTUVWXYZ")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/MANIFEST.MF")).isFalse()
  }
}
