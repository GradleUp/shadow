package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.isRegular
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TransformersTest : BaseTransformerTest() {

  @Test
  fun manifestRetained() {
    writeMainClass()
    projectScriptPath.appendText(
      """
        jar {
          manifest {
            attributes 'Main-Class': 'shadow.Main'
            attributes 'Test-Entry': 'PASSED'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val mf = outputShadowJar.manifest
    assertThat(mf).isNotNull()
    assertThat(mf.mainAttributes.getValue("Test-Entry")).isEqualTo("PASSED")
    assertThat(mf.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
  }

  @Test
  fun manifestTransformed() {
    writeMainClass()

    projectScriptPath.appendText(MANIFEST_ATTRS)

    run(shadowJarTask)

    val mf = outputShadowJar.manifest
    assertThat(mf).isNotNull()
    assertThat(mf.mainAttributes.getValue("Test-Entry")).isEqualTo("PASSED")
    assertThat(mf.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
    assertThat(mf.mainAttributes.getValue("New-Entry")).isEqualTo("NEW")
  }

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

    assertThat(outputShadowJar.getContent(propertiesXml).trimIndent()).isEqualTo(
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

  @Issue("https://github.com/GradleUp/shadow/issues/82")
  @Test
  fun shadowManifestLeaksToJarManifest() {
    writeMainClass()
    projectScriptPath.appendText(MANIFEST_ATTRS)

    run("jar", shadowJarTask)

    val mf1 = outputShadowJar.manifest
    assertThat(mf1).isNotNull()
    assertThat(mf1.mainAttributes.getValue("Test-Entry")).isEqualTo("PASSED")
    assertThat(mf1.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
    assertThat(mf1.mainAttributes.getValue("New-Entry")).isEqualTo("NEW")

    val mf2 = jarPath("build/libs/shadow-1.0.jar").manifest
    assertThat(mf2).isNotNull()
    assertThat(mf2.mainAttributes.getValue("Test-Entry")).isEqualTo("FAILED")
    assertThat(mf2.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
    assertThat(mf2.mainAttributes.getValue("New-Entry")).isNull()
  }

  @ParameterizedTest
  @MethodSource("transformerConfigurations")
  fun otherTransformers(pair: Pair<String, KClass<*>>) {
    val (configuration, transformer) = pair
    if (configuration.contains("test/some.file")) {
      path("test/some.file").writeText("some content")
    }
    projectScriptPath.appendText(
      """
        $shadowJar {
          transform(${transformer.java.name}) $configuration
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).isRegular()
  }

  private companion object {
    val MANIFEST_ATTRS = """
        jar {
          manifest {
            attributes 'Main-Class': 'shadow.Main'
            attributes 'Test-Entry': 'FAILED'
          }
        }
        $shadowJar {
          manifest {
            attributes 'New-Entry': 'NEW'
            attributes 'Test-Entry': 'PASSED'
          }
        }
    """.trimIndent()

    @JvmStatic
    fun transformerConfigurations() = listOf(
      "" to ApacheLicenseResourceTransformer::class,
      "" to ApacheNoticeResourceTransformer::class,
      "" to ComponentsXmlResourceTransformer::class,
      "" to DontIncludeResourceTransformer::class,
      "{ resource.set(\"test.file\"); file.fileValue(file(\"test/some.file\")) }" to IncludeResourceTransformer::class,
      "" to Log4j2PluginsCacheFileTransformer::class,
      "" to ManifestAppenderTransformer::class,
      "" to ManifestResourceTransformer::class,
      "{ keyTransformer = { it.toLowerCase() } }" to PropertiesFileTransformer::class,
    )
  }
}
