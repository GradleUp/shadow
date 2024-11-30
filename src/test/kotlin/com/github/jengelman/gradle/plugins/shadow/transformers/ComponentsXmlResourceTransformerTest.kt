package com.github.jengelman.gradle.plugins.shadow.transformers

import org.codehaus.plexus.util.IOUtil
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.XMLAssert
import org.custommonkey.xmlunit.XMLUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Modified from org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformerTest.java
 */
class ComponentsXmlResourceTransformerTest extends TransformerTestSupport<ComponentsXmlResourceTransformer> {
  @BeforeEach
  void setUp() {
    transformer = new ComponentsXmlResourceTransformer()
  }

  @Test
  void testConfigurationMerging() {

    XMLUnit.setNormalizeWhitespace(true)

    transformer.transform(
      TransformerContext.builder()
        .path("components-1.xml")
        .inputStream(requireResourceAsStream("components-1.xml"))
        .build()
    )
    transformer.transform(
      TransformerContext.builder()
        .path("components-1.xml")
        .inputStream(requireResourceAsStream("components-2.xml"))
        .build()
    )
    Diff diff = XMLUnit.compareXML(
      IOUtil.toString(requireResourceAsStream("components-expected.xml"), "UTF-8"),
      IOUtil.toString(transformer.transformedResource, "UTF-8"))
    XMLAssert.assertXMLIdentical(diff, true)
  }
}
