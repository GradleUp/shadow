package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
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
  private static ShadowStats stats

  @BeforeEach
  void setUp() {
    transformer = new ComponentsXmlResourceTransformer()
    stats = new ShadowStats()
  }

  @Test
  void testConfigurationMerging() {

    XMLUnit.setNormalizeWhitespace(true)

    transformer.transform(
      TransformerContext.builder()
        .path("components-1.xml")
        .inputStream(getClass().getResourceAsStream("/components-1.xml"))
        .relocators(Collections.<Relocator> emptyList())
        .stats(stats)
        .build())
    transformer.transform(
      TransformerContext.builder()
        .path("components-1.xml")
        .inputStream(getClass().getResourceAsStream("/components-2.xml"))
        .relocators(Collections.<Relocator> emptyList())
        .stats(stats)
        .build())
    Diff diff = XMLUnit.compareXML(
      IOUtil.toString(getClass().getResourceAsStream("/components-expected.xml"), "UTF-8"),
      IOUtil.toString(transformer.getTransformedResource(), "UTF-8"))
    //assertEquals( IOUtil.toString( getClass().getResourceAsStream( "/components-expected.xml" ), "UTF-8" ),
    //              IOUtil.toString( transformer.getTransformedResource(), "UTF-8" ).replaceAll("\r\n", "\n") )
    XMLAssert.assertXMLIdentical(diff, true)
  }
}
