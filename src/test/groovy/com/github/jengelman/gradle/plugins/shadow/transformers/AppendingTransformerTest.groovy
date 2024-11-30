package com.github.jengelman.gradle.plugins.shadow.transformers

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Modified from org.apache.maven.plugins.shade.resource.AppendingTransformerTest.java
 */
class AppendingTransformerTest extends TransformerTestSupport<AppendingTransformer> {

  static {
    /*
     * NOTE: The Turkish locale has an usual case transformation for the letters "I" and "i", making it a prime
     * choice to test for improper case-less string comparisions.
     */
    Locale.setDefault(new Locale("tr"))
  }

  @BeforeEach
  void setUp() {
    transformer = new AppendingTransformer(objectFactory)
  }

  @Test
  void testCanTransformResource() {
    transformer.resource.set("abcdefghijklmnopqrstuvwxyz")

    assertTrue(transformer.canTransformResource(getFileElement("abcdefghijklmnopqrstuvwxyz")))
    assertTrue(transformer.canTransformResource(getFileElement("ABCDEFGHIJKLMNOPQRSTUVWXYZ")))
    assertFalse(transformer.canTransformResource(getFileElement("META-INF/MANIFEST.MF")))
  }

}
