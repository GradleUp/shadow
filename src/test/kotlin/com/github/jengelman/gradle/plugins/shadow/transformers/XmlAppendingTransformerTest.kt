package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class XmlAppendingTransformerTest : TransformerTestSupport<XmlAppendingTransformer>() {

  init {
    setupTurkishLocale()
  }

  @BeforeEach
  fun setUp() {
    transformer = XmlAppendingTransformer(objectFactory)
  }

  @Test
  fun testCanTransformResource() {
    transformer.resource.set("abcdefghijklmnopqrstuvwxyz")

    assertThat(transformer.canTransformResource(getFileElement("abcdefghijklmnopqrstuvwxyz"))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement("META-INF/MANIFEST.MF"))).isFalse()
  }
}
