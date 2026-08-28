package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import kotlin.io.path.readText
import org.custommonkey.xmlunit.XMLUnit
import org.junit.jupiter.api.Test

/**
 * Modified from
 * [org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ComponentsXmlResourceTransformerTest.java).
 */
class ComponentsXmlResourceTransformerTest :
  BaseTransformerTest<ComponentsXmlResourceTransformer>() {
  @Test
  fun configurationMerging() =
    with(transformer) {
      XMLUnit.setNormalizeWhitespace(true)
      transform(resourceContext("components-1.xml"))
      transform(resourceContext("components-2.xml"))

      val actualXml = transformedResource.decodeToString()
      val diff =
        XMLUnit.compareXML(
          requireResourceAsPath("components-expected.xml").readText(),
          actualXml,
        )
      assertThat(diff.identical()).isTrue()
      assertThat(actualXml)
        .isEqualTo(
          $$"""
          |<component-set>
          |  <components>
          |    <component>
          |      <role>org.apache.maven.wagon.Wagon</role>
          |      <role-hint>http</role-hint>
          |      <implementation>org.apache.maven.wagon.providers.http.LightweightHttpWagon</implementation>
          |      <instantiation-strategy>per-lookup</instantiation-strategy>
          |      <description>LightweightHttpWagon</description>
          |      <isolated-realm>false</isolated-realm>
          |      <configuration>
          |        <httpHeaders>
          |          <property>
          |            <name>User-Agent</name>
          |            <value>Apache Maven/${project.version}</value>
          |          </property>
          |        </httpHeaders>
          |      </configuration>
          |    </component>
          |    <component>
          |      <role>org.apache.maven.wagon.Wagon</role>
          |      <role-hint>https</role-hint>
          |      <implementation>org.apache.maven.wagon.providers.http.LightweightHttpsWagon</implementation>
          |      <instantiation-strategy>per-lookup</instantiation-strategy>
          |      <description>LIghtweightHttpsWagon</description>
          |      <isolated-realm>false</isolated-realm>
          |      <configuration>
          |        <httpHeaders>
          |          <property>
          |            <name>User-Agent</name>
          |            <value>Apache Maven/${project.version}</value>
          |          </property>
          |        </httpHeaders>
          |      </configuration>
          |    </component>
          |  </components>
          |</component-set>
          """
            .trimMargin()
        )
    }
}
