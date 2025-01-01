package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.jengelman.gradle.plugins.shadow.transformers.ApacheLicenseResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ApacheNoticeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.DontIncludeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.IncludeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestAppenderTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.util.AppendableJar
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import kotlin.io.path.appendText
import kotlin.io.path.inputStream
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TransformersTest : BasePluginTest() {

  @Test
  fun serviceResourceTransformer() {
    projectScriptPath.appendText(
      transform<ServiceFileTransformer>(
        shadowBlock = fromJar(buildJarOne(), buildJarTwo()),
        transformerBlock = """
          exclude 'META-INF/services/com.acme.*'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text1 = getJarFileContents(outputShadowJar, ENTRY_SERVICES_SHADE)
    assertThat(text1).isEqualTo(CONTENT_ONE_TWO)

    val text2 = getJarFileContents(outputShadowJar, ENTRY_SERVICES_FOO)
    assertThat(text2).isEqualTo("one")
  }

  @Test
  fun serviceResourceTransformerAlternatePath() {
    val one = buildJarOne {
      insert(ENTRY_FOO_SHADE, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_FOO_SHADE, CONTENT_TWO)
    }
    projectScriptPath.appendText(
      transform<ServiceFileTransformer>(
        shadowBlock = fromJar(one, two),
        transformerBlock = """
          path = "META-INF/foo"
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, ENTRY_FOO_SHADE)
    assertThat(text).isEqualTo(CONTENT_ONE_TWO)
  }

  @Test
  fun serviceResourceTransformerShortSyntax() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(buildJarOne(), buildJarTwo())}
          mergeServiceFiles {
            exclude("META-INF/services/com.acme.*")
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text1 = getJarFileContents(outputShadowJar, ENTRY_SERVICES_SHADE)
    assertThat(text1).isEqualTo(CONTENT_ONE_TWO)

    val text2 = getJarFileContents(outputShadowJar, ENTRY_SERVICES_FOO)
    assertThat(text2).isEqualTo("one")
  }

  @Test
  fun serviceResourceTransformerShortSyntaxRelocation() {
    val one = buildJarOne {
      insert(
        "META-INF/services/java.sql.Driver",
        "oracle.jdbc.OracleDriver\norg.apache.hive.jdbc.HiveDriver",
      )
      insert(
        "META-INF/services/org.apache.axis.components.compiler.Compiler",
        "org.apache.axis.components.compiler.Javac",
      )
      insert(
        "META-INF/services/org.apache.commons.logging.LogFactory",
        "org.apache.commons.logging.impl.LogFactoryImpl",
      )
    }
    val two = buildJarTwo {
      insert(
        "META-INF/services/java.sql.Driver",
        "org.apache.derby.jdbc.AutoloadedDriver\ncom.mysql.jdbc.Driver",
      )
      insert(
        "META-INF/services/org.apache.axis.components.compiler.Compiler",
        "org.apache.axis.components.compiler.Jikes",
      )
      insert("META-INF/services/org.apache.commons.logging.LogFactory", "org.mortbay.log.Factory")
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(one, two)}
          mergeServiceFiles()
          relocate("org.apache", "myapache") {
            exclude("org.apache.axis.components.compiler.Jikes")
            exclude("org.apache.commons.logging.LogFactory")
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text1 = getJarFileContents(outputShadowJar, "META-INF/services/java.sql.Driver")
    assertThat(text1).isEqualTo(
      """
        oracle.jdbc.OracleDriver
        myapache.hive.jdbc.HiveDriver
        myapache.derby.jdbc.AutoloadedDriver
        com.mysql.jdbc.Driver
      """.trimIndent(),
    )

    val text2 = getJarFileContents(outputShadowJar, "META-INF/services/myapache.axis.components.compiler.Compiler")
    assertThat(text2).isEqualTo(
      """
        myapache.axis.components.compiler.Javac
        org.apache.axis.components.compiler.Jikes
      """.trimIndent(),
    )

    val text3 = getJarFileContents(outputShadowJar, "META-INF/services/org.apache.commons.logging.LogFactory")
    assertThat(text3).isEqualTo(
      """
        myapache.commons.logging.impl.LogFactoryImpl
        org.mortbay.log.Factory
      """.trimIndent(),
    )
  }

  @Test
  fun serviceResourceTransformerShortSyntaxAlternatePath() {
    val one = buildJarOne {
      insert(ENTRY_FOO_SHADE, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_FOO_SHADE, CONTENT_TWO)
    }
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(one, two)}
          mergeServiceFiles("META-INF/foo")
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, ENTRY_FOO_SHADE)
    assertThat(text).isEqualTo(CONTENT_ONE_TWO)
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/70",
    "https://github.com/GradleUp/shadow/issues/71",
  )
  @Test
  fun applyTransformersToProjectResources() {
    val servicesShadowEntry = "META-INF/services/shadow.Shadow"
    val one = buildJarOne {
      insert(servicesShadowEntry, CONTENT_ONE)
    }.toUri().toURL().path
    repo.module("shadow", "two", "1.0")
      .insertFile(servicesShadowEntry, CONTENT_TWO)
      .publish()

    projectScriptPath.appendText(
      """
        dependencies {
          implementation("shadow:two:1.0")
          implementation(files('$one'))
        }
        $shadowJar {
          mergeServiceFiles()
        }
      """.trimIndent(),
    )
    path("src/main/resources/$servicesShadowEntry").writeText(CONTENT_THREE)

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, servicesShadowEntry)
    assertThat(text).isEqualTo(CONTENT_THREE + "\n" + CONTENT_ONE_TWO)
  }

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

  private fun buildJarOne(
    builder: AppendableJar.() -> AppendableJar = {
      insert(ENTRY_SERVICES_SHADE, CONTENT_ONE)
      insert(ENTRY_SERVICES_FOO, "one")
    },
  ): Path {
    return buildJar("one.jar")
      .builder()
      .write()
  }

  private fun buildJarTwo(
    builder: AppendableJar.() -> AppendableJar = {
      insert(ENTRY_SERVICES_SHADE, CONTENT_TWO)
      insert(ENTRY_SERVICES_FOO, "two")
    },
  ): Path {
    return buildJar("two.jar")
      .builder()
      .write()
  }

  private fun buildJar(path: String): AppendableJar {
    return AppendableJar(path(path))
  }

  private fun writeMainClass() {
    path("src/main/java/shadow/Main.java").writeText(
      """
        package shadow;
        public class Main {
          public static void main(String[] args) {
            System.out.println("Hello, World!");
          }
        }
      """.trimIndent(),
    )
  }

  private companion object {
    const val CONTENT_ONE = "one # NOTE: No newline terminates this line/file"
    const val CONTENT_TWO = "two # NOTE: No newline terminates this line/file"
    const val CONTENT_THREE = "three # NOTE: No newline terminates this line/file"
    const val CONTENT_ONE_TWO = "$CONTENT_ONE\n$CONTENT_TWO"

    const val ENTRY_TEST_PROPERTIES = "test.properties"
    const val ENTRY_SERVICES_SHADE = "META-INF/services/org.apache.maven.Shade"
    const val ENTRY_SERVICES_FOO = "META-INF/services/com.acme.Foo"
    const val ENTRY_FOO_SHADE = "META-INF/foo/org.apache.maven.Shade"
    const val ENTRY_SERVICE_EXTENSION_MODULE = "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
    const val ENTRY_GROOVY_EXTENSION_MODULE = "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule"

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
      "" to ServiceFileTransformer::class,
      "" to XmlAppendingTransformer::class,
    )

    fun getJarFileContents(jarPath: Path, entryName: String): String {
      JarFile(jarPath.toFile()).use { jar ->
        val entry = jar.getJarEntry(entryName) ?: error("Entry not found: $entryName")
        return jar.getInputStream(entry).bufferedReader().readText()
      }
    }

    inline fun <reified T : Transformer> transform(
      shadowBlock: String = "",
      transformerBlock: String = "",
    ): String {
      return """
      $shadowJar {
        $shadowBlock
        transform(${T::class.java.name}) {
          $transformerBlock
        }
      }
      """.trimIndent()
    }
  }
}
