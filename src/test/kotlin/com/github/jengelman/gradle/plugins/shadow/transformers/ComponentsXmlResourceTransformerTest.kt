package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isTrue
import org.custommonkey.xmlunit.XMLUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ComponentsXmlResourceTransformerTest.java).
 */
class ComponentsXmlResourceTransformerTest : TransformerTestSupport<ComponentsXmlResourceTransformer>() {

  @BeforeEach
  fun setup() {
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

    val expectedXml = requireResourceAsStream("components-expected.xml").bufferedReader().readText()
    val actualXml = transformer.transformedResource.decodeToString()
    val diff = XMLUnit.compareXML(expectedXml, actualXml)

    assertThat(diff.identical()).isTrue()
  }
}
