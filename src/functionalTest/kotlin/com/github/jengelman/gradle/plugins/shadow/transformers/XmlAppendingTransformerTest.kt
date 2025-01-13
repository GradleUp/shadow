package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class XmlAppendingTransformerTest : BaseTransformerTest() {
  @Test
  fun appendXmlFiles() {
    val propertiesXml = "properties.xml"
    val xmlContent = """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
      <properties version="1.0">
        <entry key="%s">%s</entry>
      </properties>
    """.trimIndent()

    val xml1 = buildJar("xml1.jar") {
      insert(propertiesXml, xmlContent.format("key1", "val1"))
    }
    val xml2 = buildJar("xml2.jar") {
      insert(propertiesXml, xmlContent.format("key2", "val2"))
    }

    projectScriptPath.appendText(
      transform<XmlAppendingTransformer>(
        shadowJarBlock = fromJar(xml1, xml2),
        transformerBlock = """
          resource = 'properties.xml'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(propertiesXml) }.trimIndent()
    assertThat(content).isEqualTo(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
        <properties version="1.0">
          <entry key="key1">val1</entry>
          <entry key="key2">val2</entry>
        </properties>
      """.trimIndent(),
    )
  }

  @Test
  fun canBundleMetaInfoPluginXml() {
    val xmlEntry = "META-INF/plugin.xml"
    val xmlContent = """
      <?xml version="1.0" encoding="UTF-8"?>
      <plugin>
        <id>my.plugin.id</id>
      </plugin>
    """.trimIndent()
    val pluginJar = buildJar("plugin.jar") {
      insert(xmlEntry, xmlContent)
    }

    projectScriptPath.appendText(
      transform<XmlAppendingTransformer>(
        shadowJarBlock = fromJar(pluginJar),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(xmlEntry) }.trimIndent()
    assertThat(content).isEqualTo(xmlContent)
  }
}
