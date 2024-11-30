package com.github.jengelman.gradle.plugins.shadow.transformers

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Modified from org.apache.maven.plugins.shade.resource.ApacheLicenseResourceTransformerTest.java
 */
class ApacheLicenseResourceTransformerTest extends TransformerTestSupport<ApacheLicenseResourceTransformer> {

  static {
    setupTurkishLocale()
  }

  @BeforeEach
  void setUp() {
    transformer = new ApacheLicenseResourceTransformer()
  }

  @Test
  void testCanTransformResource() {
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/LICENSE")))
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/LICENSE.TXT")))
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/License.txt")))
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/LICENSE.md")))
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/License.md")))
    assertFalse(transformer.canTransformResource(getFileElement("META-INF/MANIFEST.MF")))
  }
}
