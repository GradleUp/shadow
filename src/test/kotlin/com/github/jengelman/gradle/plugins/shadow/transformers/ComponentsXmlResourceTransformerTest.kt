package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsStream
import org.custommonkey.xmlunit.XMLUnit
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ComponentsXmlResourceTransformerTest.java).
 */
class ComponentsXmlResourceTransformerTest : BaseTransformerTest<ComponentsXmlResourceTransformer>() {
  @Test
  fun configurationMerging() {
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
    val diff = XMLUnit.compareXML(
      requireResourceAsStream("components-expected.xml").bufferedReader().readText(),
      transformer.transformedResource.decodeToString(),
    )
    assertThat(diff.identical()).isTrue()
  }
}
