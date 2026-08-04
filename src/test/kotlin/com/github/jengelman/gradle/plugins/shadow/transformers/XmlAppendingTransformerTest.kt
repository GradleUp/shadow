package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import org.junit.jupiter.api.Test

class XmlAppendingTransformerTest : BaseTransformerTest<XmlAppendingTransformer>() {

  init {
    setupTurkishLocale()
  }

  @Test
  fun canTransformResource() =
    with(transformer) {
      resource.set("abcdefghijklmnopqrstuvwxyz")

      assertThat(canTransformResource("abcdefghijklmnopqrstuvwxyz")).isTrue()
      assertThat(canTransformResource("ABCDEFGHIJKLMNOPQRSTUVWXYZ")).isTrue()
      assertThat(canTransformResource("META-INF/MANIFEST.MF")).isFalse()
    }

  @Test
  fun appendXmlFiles() =
    with(transformer) {
      val xmlEntry = "properties.xml"
      resource.set(xmlEntry)
      val xmlContent =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE properties SYSTEM "https://java.sun.com/dtd/properties.dtd">
        <properties version="1.0">
          <entry key="%s">%s</entry>
        </properties>
        """
          .trimIndent()

      transform(textContext(xmlEntry, xmlContent.format("key1", "val1")))
      transform(textContext(xmlEntry, xmlContent.format("key2", "val2")))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val content = JarPath(tempJar).use { it.getContent(xmlEntry) }.normalizeXmlEol()
      assertThat(content)
        .isEqualTo(
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <!DOCTYPE properties SYSTEM "https://java.sun.com/dtd/properties.dtd">
          <properties version="1.0">
            <entry key="key1">val1</entry>
            <entry key="key2">val2</entry>
          </properties>
          """
            .trimIndent() + "\n"
        )
    }

  @Test
  fun appendXmlFilesWithUnreachableDtd() =
    with(transformer) {
      val xmlEntry = "properties_invalid_dtd.xml"
      resource.set(xmlEntry)
      val xmlContent =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE properties SYSTEM "https://example.invalid/dtd/properties.dtd">
        <properties version="1.0">
          <entry key="%s">%s</entry>
        </properties>
        """
          .trimIndent()

      transform(textContext(xmlEntry, xmlContent.format("key1", "val1")))
      transform(textContext(xmlEntry, xmlContent.format("key2", "val2")))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val content = JarPath(tempJar).use { it.getContent(xmlEntry) }.normalizeXmlEol()
      assertThat(content)
        .isEqualTo(
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <!DOCTYPE properties SYSTEM "https://example.invalid/dtd/properties.dtd">
          <properties version="1.0">
            <entry key="key1">val1</entry>
            <entry key="key2">val2</entry>
          </properties>
          """
            .trimIndent() + "\n"
        )
    }

  @Test // #168
  fun mergeNestedLevels() =
    with(transformer) {
      val xmlEntry = "META-INF/nested.xml"
      resource.set(xmlEntry)
      val xmlContent =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <a>%s</a>
        """
          .trimIndent()

      transform(textContext(xmlEntry, xmlContent.format("<b />")))
      transform(textContext(xmlEntry, xmlContent.format("<c />")))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val content = JarPath(tempJar).use { it.getContent(xmlEntry) }.normalizeXmlEol()
      assertThat(content)
        .isEqualTo(
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <a>
            <b />
            <c />
          </a>
          """
            .trimIndent() + "\n"
        )
    }

  private companion object {
    /**
     * Normalizes line breaks in XML content produced by [XmlAppendingTransformer].
     *
     * JDOM2's [org.jdom2.output.Format.getPrettyFormat] defaults its line separator to `\r\n`
     * (CRLF) across all platforms. Replacing `\r\n` with `\n` aligns the output with Kotlin's raw
     * string literals (`"""...""".trimIndent()`) for cross-platform assertions.
     */
    fun String.normalizeXmlEol(): String = replace("\r\n", "\n")
  }
}
