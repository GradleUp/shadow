package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import kotlin.io.path.readText
import org.custommonkey.xmlunit.XMLUnit
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ComponentsXmlResourceTransformerTest.java).
 */
class ComponentsXmlResourceTransformerTest : BaseTransformerTest<ComponentsXmlResourceTransformer>() {
  @Test
  fun configurationMerging() {
    XMLUnit.setNormalizeWhitespace(true)
    transformer.transform(resourceContext("components-1.xml"))
    transformer.transform(resourceContext("components-2.xml"))

    val diff = XMLUnit.compareXML(
      requireResourceAsPath("components-expected.xml").readText(),
      transformer.transformedResource.decodeToString(),
    )
    assertThat(diff.identical()).isTrue()
  }
}
