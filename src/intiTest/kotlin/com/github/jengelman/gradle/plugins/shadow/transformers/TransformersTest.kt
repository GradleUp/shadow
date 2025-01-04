package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import java.util.jar.JarInputStream
import kotlin.io.path.appendText
import kotlin.io.path.inputStream
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TransformersTest : BaseTransformerTest() {

  @Test
  fun appendingTransformer() {
    val one = buildJarOne {
      insert(ENTRY_TEST_PROPERTIES, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_TEST_PROPERTIES, CONTENT_TWO)
    }
    projectScriptPath.appendText(
      transform<AppendingTransformer>(
        shadowBlock = fromJar(one, two),
        transformerBlock = """
          resource = '$ENTRY_TEST_PROPERTIES'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, ENTRY_TEST_PROPERTIES)
    assertThat(text.trimIndent()).isEqualTo(CONTENT_ONE_TWO)
  }

  @Test
  fun appendingTransformerShortSyntax() {
    val one = buildJarOne {
      insert(ENTRY_TEST_PROPERTIES, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_TEST_PROPERTIES, CONTENT_TWO)
    }
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(one, two)}
          append('$ENTRY_TEST_PROPERTIES')
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, ENTRY_TEST_PROPERTIES)
    assertThat(text.trimIndent()).isEqualTo(CONTENT_ONE_TWO)
  }

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

    assertThat(outputShadowJar).exists()

    JarInputStream(outputShadowJar.inputStream()).use { jis ->
      val mf = jis.manifest
      assertThat(mf).isNotNull()
      assertThat(mf.mainAttributes.getValue("Test-Entry")).isEqualTo("PASSED")
      assertThat(mf.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
    }
  }

  @Test
  fun manifestTransformed() {
    writeMainClass()

    projectScriptPath.appendText(
      """
        jar {
          manifest {
            attributes 'Main-Class': 'shadow.Main'
            attributes 'Test-Entry': 'FAILED'
          }
        }
        $shadowJar {
          manifest {
            attributes 'Test-Entry': 'PASSED'
            attributes 'New-Entry': 'NEW'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    JarInputStream(outputShadowJar.inputStream()).use { jis ->
      val mf = jis.manifest
      assertThat(mf).isNotNull()
      assertThat(mf.mainAttributes.getValue("Test-Entry")).isEqualTo("PASSED")
      assertThat(mf.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
      assertThat(mf.mainAttributes.getValue("New-Entry")).isEqualTo("NEW")
    }
  }

  @Test
  fun appendXmlFiles() {
    val propertiesXml = "properties.xml"
    val xml1 = buildJar("xml1.jar")
      .insert(
        propertiesXml,
        """
        <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
        <properties version="1.0">
          <entry key="key1">val1</entry>
        </properties>
        """.trimIndent(),
      ).write()
    val xml2 = buildJar("xml2.jar").insert(
      propertiesXml,
      """
        <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
        <properties version="1.0">
          <entry key="key2">val2</entry>
        </properties>
      """.trimIndent(),
    ).write()

    projectScriptPath.appendText(
      transform<XmlAppendingTransformer>(
        shadowBlock = fromJar(xml1, xml2),
        transformerBlock = """
          resource = "properties.xml"
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, propertiesXml)
    assertThat(text.trimIndent()).isEqualTo(
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
    projectScriptPath.appendText(
      """
        jar {
          manifest {
            attributes 'Main-Class': 'shadow.Main'
            attributes 'Test-Entry': 'FAILED'
          }
        }
        $shadowJar {
          manifest {
            attributes 'Test-Entry': 'PASSED'
            attributes 'New-Entry': 'NEW'
          }
        }
      """.trimIndent(),
    )

    run("jar", shadowJarTask)

    val jar = path("build/libs/shadow-1.0.jar")
    assertThat(jar).exists()
    assertThat(outputShadowJar).exists()

    JarInputStream(outputShadowJar.inputStream()).use { jis ->
      val mf = jis.manifest
      assertThat(mf).isNotNull()
      assertThat(mf.mainAttributes.getValue("Test-Entry")).isEqualTo("PASSED")
      assertThat(mf.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
      assertThat(mf.mainAttributes.getValue("New-Entry")).isEqualTo("NEW")
    }
    JarInputStream(jar.inputStream()).use { jis ->
      val mf = jis.manifest
      assertThat(mf).isNotNull()
      assertThat(mf.mainAttributes.getValue("Test-Entry")).isEqualTo("FAILED")
      assertThat(mf.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
      assertThat(mf.mainAttributes.getValue("New-Entry")).isNull()
    }
  }

  @Test
  fun groovyExtensionModuleTransformer() {
    val one = buildJarOne {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=foo
          moduleVersion=1.0.5
          extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
          staticExtensionClasses=com.acme.foo.FooStaticExtension
        """.trimIndent(),
      )
    }
    val two = buildJarTwo {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=bar
          moduleVersion=2.3.5
          extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
          staticExtensionClasses=com.acme.bar.SomeStaticExtension
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(
      transform<GroovyExtensionModuleTransformer>(
        shadowBlock = fromJar(one, two),
      ),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val props = getJarFileContents(outputShadowJar, ENTRY_SERVICE_EXTENSION_MODULE).toProperties()
    assertThat(props.getProperty("moduleName")).isEqualTo("MergedByShadowJar")
    assertThat(props.getProperty("moduleVersion")).isEqualTo("1.0.0")
    assertThat(props.getProperty("extensionClasses"))
      .isEqualTo("com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension")
    assertThat(props.getProperty("staticExtensionClasses"))
      .isEqualTo("com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension")
  }

  @Test
  fun groovyExtensionModuleTransformerWorksForGroovy25Plus() {
    val one = buildJarOne {
      insert(
        ENTRY_GROOVY_EXTENSION_MODULE,
        """
          moduleName=foo
          moduleVersion=1.0.5
          extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
          staticExtensionClasses=com.acme.foo.FooStaticExtension
        """.trimIndent(),
      )
    }
    val two = buildJarTwo {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=bar
          moduleVersion=2.3.5
          extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
          staticExtensionClasses=com.acme.bar.SomeStaticExtension
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(
      transform<GroovyExtensionModuleTransformer>(
        shadowBlock = fromJar(one, two),
      ),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val props = getJarFileContents(outputShadowJar, ENTRY_GROOVY_EXTENSION_MODULE).toProperties()
    assertThat(props.getProperty("moduleName")).isEqualTo("MergedByShadowJar")
    assertThat(props.getProperty("moduleVersion")).isEqualTo("1.0.0")
    assertThat(props.getProperty("extensionClasses"))
      .isEqualTo("com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension")
    assertThat(props.getProperty("staticExtensionClasses"))
      .isEqualTo("com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension")
    assertDoesNotContain(outputShadowJar, listOf(ENTRY_SERVICE_EXTENSION_MODULE))
  }

  @Test
  fun groovyExtensionModuleTransformerShortSyntax() {
    val one = buildJarOne {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=foo
          moduleVersion=1.0.5
          extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
          staticExtensionClasses=com.acme.foo.FooStaticExtension
        """.trimIndent(),
      )
    }
    val two = buildJarTwo {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=bar
          moduleVersion=2.3.5
          extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
          staticExtensionClasses=com.acme.bar.SomeStaticExtension
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(one, two)}
          mergeGroovyExtensionModules()
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val props = getJarFileContents(outputShadowJar, ENTRY_SERVICE_EXTENSION_MODULE).toProperties()
    assertThat(props.getProperty("moduleName")).isEqualTo("MergedByShadowJar")
    assertThat(props.getProperty("moduleVersion")).isEqualTo("1.0.0")
    assertThat(props.getProperty("extensionClasses"))
      .isEqualTo("com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension")
    assertThat(props.getProperty("staticExtensionClasses"))
      .isEqualTo("com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension")
  }

  @ParameterizedTest
  @MethodSource("transformerConfigurations")
  fun transformerShouldNotHaveDeprecatedBehaviours(pair: Pair<String, KClass<*>>) {
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

    assertThat(outputShadowJar).exists()
  }

  private companion object {
    @JvmStatic
    fun transformerConfigurations() = listOf(
      "" to ApacheLicenseResourceTransformer::class,
      "" to ApacheNoticeResourceTransformer::class,
      "" to AppendingTransformer::class,
      "" to ComponentsXmlResourceTransformer::class,
      "" to DontIncludeResourceTransformer::class,
      "" to GroovyExtensionModuleTransformer::class,
      "{ resource.set(\"test.file\"); file.fileValue(file(\"test/some.file\")) }" to IncludeResourceTransformer::class,
      "" to Log4j2PluginsCacheFileTransformer::class,
      "" to ManifestAppenderTransformer::class,
      "" to ManifestResourceTransformer::class,
      "{ keyTransformer = { it.toLowerCase() } }" to PropertiesFileTransformer::class,
      "" to XmlAppendingTransformer::class,
    )
  }
}
