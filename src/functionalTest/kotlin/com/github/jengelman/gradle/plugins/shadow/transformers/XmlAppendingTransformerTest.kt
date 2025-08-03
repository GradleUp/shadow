package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class XmlAppendingTransformerTest : BaseTransformerTest() {
  @Test
  fun appendXmlFiles() {
    val xmlEntry = "properties.xml"
    val xmlContent = """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
      <properties version="1.0">
        <entry key="%s">%s</entry>
      </properties>
    """.trimIndent()
    val one = buildJarOne {
      insert(xmlEntry, xmlContent.format("key1", "val1"))
    }
    val two = buildJarTwo {
      insert(xmlEntry, xmlContent.format("key2", "val2"))
    }

    projectScriptPath.appendText(
      transform<XmlAppendingTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          resource = '$xmlEntry'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(xmlEntry) }.trimIndent()
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

  @Issue(
    "https://github.com/GradleUp/shadow/issues/168",
  )
  @Test
  fun mergeNestedLevels() {
    val xmlEntry = "META-INF/nested.xml"
    val xmlContent = """
      <?xml version="1.0" encoding="UTF-8"?>
      <a>%s</a>
    """.trimIndent()
    val one = buildJarOne {
      insert(xmlEntry, xmlContent.format("<b />"))
    }
    val two = buildJarTwo {
      insert(xmlEntry, xmlContent.format("<c />"))
    }

    projectScriptPath.appendText(
      transform<XmlAppendingTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          resource = '$xmlEntry'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(xmlEntry) }.trimIndent()
    assertThat(content).isEqualTo(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <a>
          <b />
          <c />
        </a>
      """.trimIndent(),
    )
  }
}
