package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsStream
import org.custommonkey.xmlunit.XMLUnit
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ComponentsXmlResourceTransformerTest.java).
 */
class ComponentsXmlResourceTransformerTest : BaseTransformerTest<ComponentsXmlResourceTransformer>() {
  @Test
  fun configurationMerging() {
    XMLUnit.setNormalizeWhitespace(true)
    transformer.transform(context("components-1.xml"))
    transformer.transform(context("components-2.xml"))

    val diff = XMLUnit.compareXML(
      requireResourceAsStream("components-expected.xml").bufferedReader().readText(),
      transformer.transformedResource.decodeToString(),
    )
    assertThat(diff.identical()).isTrue()
  }

  private companion object {
    fun context(path: String): TransformerContext {
      return TransformerContext(path, requireResourceAsStream(path))
    }
  }
}
