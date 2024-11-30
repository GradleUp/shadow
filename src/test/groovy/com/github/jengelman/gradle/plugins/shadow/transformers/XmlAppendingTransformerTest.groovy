package com.github.jengelman.gradle.plugins.shadow.transformers

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Modified from org.apache.maven.plugins.shade.resource.XmlAppendingTransformerTest.java
 */
class XmlAppendingTransformerTest extends TransformerTestSupport<XmlAppendingTransformer> {

  static {
    setupTurkishLocale()
  }

  @BeforeEach
  void setUp() {
    transformer = new XmlAppendingTransformer(objectFactory)
  }

  @Test
  void testCanTransformResource() {
    transformer.resource.set("abcdefghijklmnopqrstuvwxyz")

    assertTrue(transformer.canTransformResource(getFileElement("abcdefghijklmnopqrstuvwxyz")))
    assertTrue(transformer.canTransformResource(getFileElement("ABCDEFGHIJKLMNOPQRSTUVWXYZ")))
    assertFalse(transformer.canTransformResource(getFileElement("META-INF/MANIFEST.MF")))
  }
}
