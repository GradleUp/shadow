package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isTrue
import org.codehaus.plexus.util.IOUtil
import org.custommonkey.xmlunit.XMLUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ComponentsXmlResourceTransformerTest : TransformerTestSupport<ComponentsXmlResourceTransformer>() {

  @BeforeEach
  fun setUp() {
    transformer = ComponentsXmlResourceTransformer()
  }

  @Test
  fun testConfigurationMerging() {
    XMLUnit.setNormalizeWhitespace(true)

    transformer.transform(
      TransformerContext.builder()
        .path("components-1.xml")
        .inputStream(requireResourceAsStream("components-1.xml"))
        .build(),
    )
    transformer.transform(
      TransformerContext.builder()
        .path("components-1.xml")
        .inputStream(requireResourceAsStream("components-2.xml"))
        .build(),
    )

    val expectedXml = IOUtil.toString(requireResourceAsStream("components-expected.xml"), "UTF-8")
    val actualXml = IOUtil.toString(transformer.transformedResource, "UTF-8")
    val diff = XMLUnit.compareXML(expectedXml, actualXml)

    assertThat(diff.identical()).isTrue()
  }
}
