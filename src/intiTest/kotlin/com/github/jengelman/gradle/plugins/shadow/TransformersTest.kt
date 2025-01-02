package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.util.AppendableJar
import java.nio.file.Path
import java.util.Properties
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import kotlin.io.path.appendText
import kotlin.io.path.inputStream
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TransformersTest : BasePluginTest() {

  @Test
  fun serviceResourceTransformer() {
    val one = buildJar("one.jar")
      .insert("META-INF/services/org.apache.maven.Shade", "one # NOTE: No newline terminates this line/file")
      .insert("META-INF/services/com.acme.Foo", "one")
      .write().toUri().toURL().path

    val two = buildJar("two.jar")
      .insert("META-INF/services/org.apache.maven.Shade", "two # NOTE: No newline terminates this line/file")
      .insert("META-INF/services/com.acme.Foo", "two")
      .write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        $shadowJar {
          from('$one')
          from('$two')
          transform(com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer) {
            exclude("META-INF/services/com.acme.*")
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text1 = getJarFileContents(outputShadowJar, "META-INF/services/org.apache.maven.Shade")
    assertThat(text1).isEqualTo(
      """
        one # NOTE: No newline terminates this line/file
        two # NOTE: No newline terminates this line/file
      """.trimIndent(),
    )

    val text2 = getJarFileContents(outputShadowJar, "META-INF/services/com.acme.Foo")
    assertThat(text2).isEqualTo("one")
  }

  @Test
  fun serviceResourceTransformerAlternatePath() {
    val one = buildJar("one.jar")
      .insert("META-INF/foo/org.apache.maven.Shade", "one # NOTE: No newline terminates this line/file")
      .write().toUri().toURL().path
    val two = buildJar("two.jar")
      .insert("META-INF/foo/org.apache.maven.Shade", "two # NOTE: No newline terminates this line/file")
      .write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        $shadowJar {
          from('$one')
          from('$two')
          transform(com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer) {
            path = "META-INF/foo"
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, "META-INF/foo/org.apache.maven.Shade")
    assertThat(text).isEqualTo(
      """
        one # NOTE: No newline terminates this line/file
        two # NOTE: No newline terminates this line/file
      """.trimIndent(),
    )
  }

  @Test
  fun serviceResourceTransformerShortSyntax() {
    val one = buildJar("one.jar")
      .insert("META-INF/services/org.apache.maven.Shade", "one # NOTE: No newline terminates this line/file")
      .insert("META-INF/services/com.acme.Foo", "one")
      .write().toUri().toURL().path

    val two = buildJar("two.jar")
      .insert("META-INF/services/org.apache.maven.Shade", "two # NOTE: No newline terminates this line/file")
      .insert("META-INF/services/com.acme.Foo", "two")
      .write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        $shadowJar {
          from('$one')
          from('$two')
          mergeServiceFiles {
            exclude("META-INF/services/com.acme.*")
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text1 = getJarFileContents(outputShadowJar, "META-INF/services/org.apache.maven.Shade")
    assertThat(text1).isEqualTo(
      """
        one # NOTE: No newline terminates this line/file
        two # NOTE: No newline terminates this line/file
      """.trimIndent(),
    )

    val text2 = getJarFileContents(outputShadowJar, "META-INF/services/com.acme.Foo")
    assertThat(text2).isEqualTo("one")
  }

  @Test
  fun serviceResourceTransformerShortSyntaxRelocation() {
    val one = buildJar("one.jar")
      .insert("META-INF/services/java.sql.Driver", "oracle.jdbc.OracleDriver\norg.apache.hive.jdbc.HiveDriver")
      .insert(
        "META-INF/services/org.apache.axis.components.compiler.Compiler",
        "org.apache.axis.components.compiler.Javac",
      )
      .insert(
        "META-INF/services/org.apache.commons.logging.LogFactory",
        "org.apache.commons.logging.impl.LogFactoryImpl",
      )
      .write().toUri().toURL().path

    val two = buildJar("two.jar")
      .insert("META-INF/services/java.sql.Driver", "org.apache.derby.jdbc.AutoloadedDriver\ncom.mysql.jdbc.Driver")
      .insert(
        "META-INF/services/org.apache.axis.components.compiler.Compiler",
        "org.apache.axis.components.compiler.Jikes",
      )
      .insert("META-INF/services/org.apache.commons.logging.LogFactory", "org.mortbay.log.Factory")
      .write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        $shadowJar {
          from('$one')
          from('$two')
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
    val one = buildJar("one.jar")
      .insert("META-INF/foo/org.apache.maven.Shade", "one # NOTE: No newline terminates this line/file")
      .write().toUri().toURL().path

    val two = buildJar("two.jar")
      .insert("META-INF/foo/org.apache.maven.Shade", "two # NOTE: No newline terminates this line/file")
      .write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        $shadowJar {
          from('$one')
          from('$two')
          mergeServiceFiles("META-INF/foo")
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, "META-INF/foo/org.apache.maven.Shade")
    assertThat(text).isEqualTo(
      """
        one # NOTE: No newline terminates this line/file
        two # NOTE: No newline terminates this line/file
      """.trimIndent(),
    )
  }

  @Test
  fun applyTransformersToProjectResources() {
    val one = buildJar("one.jar")
      .insert("META-INF/services/shadow.Shadow", "one # NOTE: No newline terminates this line/file")
      .write().toUri().toURL().path

    repo.module("shadow", "two", "1.0")
      .insertFile("META-INF/services/shadow.Shadow", "two # NOTE: No newline terminates this line/file")
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

    path("src/main/resources/META-INF/services/shadow.Shadow").writeText("three # NOTE: No newline terminates this line/file")

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, "META-INF/services/shadow.Shadow")
    assertThat(text).isEqualTo(
      """
        three # NOTE: No newline terminates this line/file
        one # NOTE: No newline terminates this line/file
        two # NOTE: No newline terminates this line/file
      """.trimIndent(),
    )
  }

  @Test
  fun appendingTransformer() {
    val one = buildJar("one.jar")
      .insert("test.properties", "one # NOTE: No newline terminates this line/file")
      .write().toUri().toURL().path

    val two = buildJar("two.jar")
      .insert("test.properties", "two # NOTE: No newline terminates this line/file")
      .write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        $shadowJar {
          from('$one')
          from('$two')
          transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer) {
            resource = "test.properties"
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, "test.properties")
    assertThat(text.trimIndent()).isEqualTo(
      """
        one # NOTE: No newline terminates this line/file
        two # NOTE: No newline terminates this line/file
      """.trimIndent(),
    )
  }

  @Test
  fun appendingTransformerShortSyntax() {
    val one = buildJar("one.jar")
      .insert("test.properties", "one # NOTE: No newline terminates this line/file")
      .write().toUri().toURL().path

    val two = buildJar("two.jar")
      .insert("test.properties", "two # NOTE: No newline terminates this line/file")
      .write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        $shadowJar {
          from('$one')
          from('$two')
          append("test.properties")
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, "test.properties")
    assertThat(text.trimIndent()).isEqualTo(
      """
        one # NOTE: No newline terminates this line/file
        two # NOTE: No newline terminates this line/file
      """.trimIndent(),
    )
  }

  @Test
  fun manifestRetained() {
    path("src/main/java/shadow/Main.java").writeText(
      """
        package shadow;
        public class Main {
          public static void main(String[] args) { }
        }
      """.trimIndent(),
    )

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
    path("src/main/java/shadow/Main.java").writeText(
      """
        package shadow;

        public class Main {
          public static void main(String[] args) { }
        }
      """.trimIndent(),
    )

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
    val xml1 = buildJar("xml1.jar").insert(
      "properties.xml",
      """
        <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">

        <properties version="1.0">
          <entry key="key1">val1</entry>
        </properties>
      """.trimIndent(),
    ).write().toUri().toURL().path

    val xml2 = buildJar("xml2.jar").insert(
      "properties.xml",
      """
        <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">

        <properties version="1.0">
          <entry key="key2">val2</entry>
        </properties>
      """.trimIndent(),
    ).write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        $shadowJar {
          from('$xml1')
          from('$xml2')
          transform(com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer) {
            resource = "properties.xml"
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, "properties.xml")
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

  @Test
  fun shadowManifestLeaksToJarManifest() {
    path("src/main/java/shadow/Main.java").writeText(
      """
        package shadow;
        public class Main {
          public static void main(String[] args) { }
        }
      """.trimIndent(),
    )

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

    JarInputStream(jar.inputStream()).use { jis2 ->
      val mf2 = jis2.manifest
      assertThat(mf2).isNotNull()
      assertThat(mf2.mainAttributes.getValue("Test-Entry")).isEqualTo("FAILED")
      assertThat(mf2.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
      assertThat(mf2.mainAttributes.getValue("New-Entry")).isNull()
    }
  }

  @Test
  fun groovyExtensionModuleTransformer() {
    val one = buildJar("one.jar")
      .insert(
        "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule",
        """
          moduleName=foo
          moduleVersion=1.0.5
          extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
          staticExtensionClasses=com.acme.foo.FooStaticExtension
        """.trimIndent(),
      ).write().toUri().toURL().path

    val two = buildJar("two.jar")
      .insert(
        "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule",
        """
          moduleName=bar
          moduleVersion=2.3.5
          extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
          staticExtensionClasses=com.acme.bar.SomeStaticExtension
        """.trimIndent(),
      ).write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        import ${GroovyExtensionModuleTransformer::class.java.name}
        $shadowJar {
          from('$one')
          from('$two')
          transform(${GroovyExtensionModuleTransformer::class.java.name})
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule")
    val props = Properties().apply { load(text.reader()) }
    assertThat(props.getProperty("moduleName")).isEqualTo("MergedByShadowJar")
    assertThat(props.getProperty("moduleVersion")).isEqualTo("1.0.0")
    assertThat(props.getProperty("extensionClasses"))
      .isEqualTo("com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension")
    assertThat(props.getProperty("staticExtensionClasses"))
      .isEqualTo("com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension")
  }

  @Test
  fun groovyExtensionModuleTransformerWorksForGroovy25Plus() {
    val one = buildJar("one.jar")
      .insert(
        "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule",
        """
          moduleName=foo
          moduleVersion=1.0.5
          extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
          staticExtensionClasses=com.acme.foo.FooStaticExtension
        """.trimIndent(),
      ).write().toUri().toURL().path

    val two = buildJar("two.jar")
      .insert(
        "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule",
        """
          moduleName=bar
          moduleVersion=2.3.5
          extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
          staticExtensionClasses=com.acme.bar.SomeStaticExtension
        """.trimIndent(),
      ).write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        import ${GroovyExtensionModuleTransformer::class.java.name}
        $shadowJar {
          from('$one')
          from('$two')
          transform(${GroovyExtensionModuleTransformer::class.java.name})
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule")
    val props = Properties().apply { load(text.reader()) }
    assertThat(props.getProperty("moduleName")).isEqualTo("MergedByShadowJar")
    assertThat(props.getProperty("moduleVersion")).isEqualTo("1.0.0")
    assertThat(props.getProperty("extensionClasses"))
      .isEqualTo("com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension")
    assertThat(props.getProperty("staticExtensionClasses"))
      .isEqualTo("com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension")
    assertDoesNotContain(outputShadowJar, listOf("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"))
  }

  @Test
  fun groovyExtensionModuleTransformerShortSyntax() {
    val one = buildJar("one.jar")
      .insert(
        "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule",
        """
          moduleName=foo
          moduleVersion=1.0.5
          extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
          staticExtensionClasses=com.acme.foo.FooStaticExtension
        """.trimIndent(),
      ).write().toUri().toURL().path

    val two = buildJar("two.jar")
      .insert(
        "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule",
        """
          moduleName=bar
          moduleVersion=2.3.5
          extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
          staticExtensionClasses=com.acme.bar.SomeStaticExtension
        """.trimIndent(),
      ).write().toUri().toURL().path

    projectScriptPath.appendText(
      """
        $shadowJar {
          from('$one')
          from('$two')
          mergeGroovyExtensionModules()
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val text = getJarFileContents(outputShadowJar, "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule")
    val props = Properties().apply { load(text.reader()) }
    assertThat(props.getProperty("moduleName")).isEqualTo("MergedByShadowJar")
    assertThat(props.getProperty("moduleVersion")).isEqualTo("1.0.0")
    assertThat(props.getProperty("extensionClasses"))
      .isEqualTo("com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension")
    assertThat(props.getProperty("staticExtensionClasses"))
      .isEqualTo("com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension")
  }

  @ParameterizedTest
  @MethodSource("transformerConfigurations")
  fun transformerShouldNotHaveDeprecatedBehaviours(pair: Pair<String, String>) {
    val (configuration, transformer) = pair
    if (configuration.contains("test/some.file")) {
      path("test/some.file").writeText("some content")
    }
    projectScriptPath.appendText(
      """
        $shadowJar {
          transform(com.github.jengelman.gradle.plugins.shadow.transformers.$transformer) $configuration
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()
  }

  private fun buildJar(path: String): AppendableJar {
    return AppendableJar(path(path))
  }

  private companion object {
    @JvmStatic
    fun transformerConfigurations() = listOf(
      "" to "ApacheLicenseResourceTransformer",
      "" to "ApacheNoticeResourceTransformer",
      "" to "AppendingTransformer",
      "" to "ComponentsXmlResourceTransformer",
      "" to "DontIncludeResourceTransformer",
      "" to "GroovyExtensionModuleTransformer",
      "{ resource.set(\"test.file\"); file.fileValue(file(\"test/some.file\")) }" to "IncludeResourceTransformer",
      "" to "Log4j2PluginsCacheFileTransformer",
      "" to "ManifestAppenderTransformer",
      "" to "ManifestResourceTransformer",
      "{ keyTransformer = { it.toLowerCase() } }" to "PropertiesFileTransformer",
      "" to "ServiceFileTransformer",
      "" to "XmlAppendingTransformer",
    )

    fun getJarFileContents(jarPath: Path, entryName: String): String {
      JarFile(jarPath.toFile()).use { jar ->
        val entry = jar.getJarEntry(entryName) ?: error("Entry not found: $entryName")
        return jar.getInputStream(entry).bufferedReader().readText()
      }
    }
  }
}
