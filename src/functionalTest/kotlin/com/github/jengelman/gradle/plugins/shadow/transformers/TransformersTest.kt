package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.crlfEolString
import com.github.jengelman.gradle.plugins.shadow.testkit.getBytes
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getStream
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsStream
import java.util.jar.Attributes as JarAttribute
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE
import org.junit.jupiter.api.Test

class TransformersTest : BaseTransformerTest() {

  @Test
  fun manifestRetained() {
    writeClass()
    projectScript.appendText(
      """
      |$jarTask {
      |  manifest {
      |    attributes '$mainClassAttributeKey': 'my.Main'
      |    attributes '$TEST_ENTRY_ATTR_KEY': 'PASSED'
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    commonAssertions {
      assertThat(getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("PASSED")
      assertThat(getValue(mainClassAttributeKey)).isEqualTo("my.Main")
    }
  }

  @Test
  fun manifestTransformed() {
    writeClass()

    projectScript.appendText(MANIFEST_ATTRS)

    runWithSuccess(shadowJarPath)

    commonAssertions()
  }

  @Test
  fun manifestResourceTransformer() {
    writeClass()
    projectScript.appendText(
      transform<ManifestResourceTransformer>(
        transformerBlock =
          """
          |mainClass = 'my.Main'
          |manifestEntries = ['$TEST_ENTRY_ATTR_KEY': 'PASSED', 'Number-Entry': 123]
          |attributes '$NEW_ENTRY_ATTR_KEY': 'NEW'
          """
            .trimMargin()
      )
    )

    runWithSuccess(shadowJarPath)

    commonAssertions {
      assertThat(getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("PASSED")
      assertThat(getValue(mainClassAttributeKey)).isEqualTo("my.Main")
      assertThat(getValue(NEW_ENTRY_ATTR_KEY)).isEqualTo("NEW")
      assertThat(getValue("Number-Entry")).isEqualTo("123")
    }
  }

  @Test
  fun manifestResourceTransformerRemoveAttributes() {
    writeClass()
    projectScript.appendText(
      """
      |$jarTask {
      |  manifest {
      |    attributes 'Header-To-Remove-1': 'Value1', 'Header-To-Keep': 'Value2'
      |  }
      |}
      |${transform<ManifestResourceTransformer>(
        transformerBlock =
          """
          |manifestEntries.put('Header-To-Remove-1', ${ManifestResourceTransformer::class.java.name}.NULL)
          """
            .trimMargin()
      )}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    commonAssertions {
      assertThat(getValue("Header-To-Remove-1")).isNull()
      assertThat(getValue("Header-To-Keep")).isEqualTo("Value2")
    }
  }

  @Test // #427
  fun mergeLog4j2PluginCacheFiles() {
    val content = requireResourceAsPath(PLUGIN_CACHE_FILE).readText()
    val one = buildJarOne { insert(PLUGIN_CACHE_FILE, content) }
    val two = buildJarOne { insert(PLUGIN_CACHE_FILE, content) }
    projectScript.appendText(
      transform<Log4j2PluginsCacheFileTransformer>(
        dependenciesBlock = implementationFiles(one, two)
      )
    )

    runWithSuccess(shadowJarPath)

    val actualFileBytes = outputShadowedJar.use { jar ->
      jar.getStream(PLUGIN_CACHE_FILE).use { it.readAllBytes() }
    }
    assertThat(actualFileBytes.contentHashCode()).all {
      // Hash of the original plugin cache file.
      isNotEqualTo(-2114104185)
      isEqualTo(1911442937)
    }
  }

  @Test
  fun preserveFirstFoundResource() {
    path("src/main/resources/foo/bar").writeText("bar1")
    path("src/main/resources/foo/baz").writeText("baz1")
    val one = buildJarOne {
      insert("foo/bar", "bar2")
      insert("foo/baz", "baz2")
    }
    val two = buildJarTwo {
      insert("foo/bar", "bar3")
      insert("foo/baz", "baz3")
    }
    projectScript.appendText(
      transform<PreserveFirstFoundResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = "resources = ['foo/bar']",
      )
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("foo/", "foo/bar", "foo/baz", *manifestEntries)
      getContent("foo/bar").isEqualTo("bar1")
      getContent("foo/baz").isEqualTo("baz3")
    }
  }

  @Test
  fun useCustomTransformer() {
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'my:a:1.0'
      |  implementation 'my:b:1.0'
      |}
      |$shadowJarTask {
      |  // Use Transformer.Companion (no-op) to mock a custom transformer here.
      |  transform(${ResourceTransformer.Companion::class.java.name})
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*entriesInAB, *manifestEntries) }
  }

  @Test
  fun deduplicatingResourceTransformerWithCaseSensitiveEntries() {
    val one = buildJarOne {
      insert("foo/Bar.txt", "Bar")
      insert("foo/bar.txt", "bar")
    }
    projectScript.appendText(
      transform<DeduplicatingResourceTransformer>(dependenciesBlock = implementationFiles(one))
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("foo/", "foo/Bar.txt", "foo/bar.txt", *manifestEntries)
      getContent("foo/Bar.txt").isEqualTo("Bar")
      getContent("foo/bar.txt").isEqualTo("bar")
    }
  }

  @Test
  fun apacheLicenseResourceTransformer() {
    val one = buildJarOne {
      insert("META-INF/LICENSE", "License 1")
      insert("foo/bar.txt", "bar")
    }
    val two = buildJarTwo {
      insert("META-INF/LICENSE.txt", "License 2")
      insert("foo/baz.txt", "baz")
    }
    projectScript.appendText(
      transform<ApacheLicenseResourceTransformer>(dependenciesBlock = implementationFiles(one, two))
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("foo/", "foo/bar.txt", "foo/baz.txt", *manifestEntries)
    }
  }

  @Test
  fun apacheNoticeResourceTransformer() {
    val one = buildJarOne {
      insert("META-INF/NOTICE", "Notice from A")
    }
    val two = buildJarTwo {
      insert("META-INF/NOTICE.txt", "Notice from B")
    }
    projectScript.appendText(
      transform<ApacheNoticeResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock =
          """
          |addHeader = false
          |copyright = 'Copyright 2026 Foo\n'
          """
            .trimMargin(),
      )
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent("META-INF/NOTICE")
        .isEqualTo(
          """
          |Copyright 2026 Foo
          |
          |This product includes software developed at
          |The Apache Software Foundation (https://www.apache.org/).
          |
          |Notice from A
          |
          |Notice from B
          """
            .trimMargin()
        )
    }
  }

  @Test
  fun componentsXmlResourceTransformer() {
    val one = buildJarOne {
      insert(
        ComponentsXmlResourceTransformer.COMPONENTS_XML_PATH,
        """
        |<component-set>
        |  <components>
        |    <component>
        |      <role>org.example.Driver</role>
        |      <role-hint>default</role-hint>
        |      <implementation>org.example.DriverImpl</implementation>
        |    </component>
        |  </components>
        |</component-set>
        """
          .trimMargin(),
      )
    }
    val two = buildJarTwo {
      insert(
        ComponentsXmlResourceTransformer.COMPONENTS_XML_PATH,
        """
        |<component-set>
        |  <components>
        |    <component>
        |      <role>org.example.Server</role>
        |      <role-hint>default</role-hint>
        |      <implementation>org.example.ServerImpl</implementation>
        |    </component>
        |  </components>
        |</component-set>
        """
          .trimMargin(),
      )
    }
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(one, two)}
      |}
      |$shadowJarTask {
      |  transform(${ComponentsXmlResourceTransformer::class.java.name})
      |  relocate('org.example', 'relocated.org.example')
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(ComponentsXmlResourceTransformer.COMPONENTS_XML_PATH)
        .isEqualTo(
          """
          |<component-set>
          |  <components>
          |    <component>
          |      <role>relocated.org.example.Driver</role>
          |      <role-hint>default</role-hint>
          |      <implementation>relocated.org.example.DriverImpl</implementation>
          |    </component>
          |    <component>
          |      <role>relocated.org.example.Server</role>
          |      <role-hint>default</role-hint>
          |      <implementation>relocated.org.example.ServerImpl</implementation>
          |    </component>
          |  </components>
          |</component-set>
          """
            .trimMargin()
        )
    }
  }

  @Test
  fun kotlinModuleMetadataTransformer() {
    val moduleBytes = requireResourceAsStream("META-INF/kotlin-stdlib.kotlin_module").readBytes()
    val one = buildJarOne {
      insert("META-INF/kotlin-stdlib.kotlin_module", moduleBytes)
    }
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(one)}
      |}
      |$shadowJarTask {
      |  transform(${KotlinModuleMetadataTransformer::class.java.name})
      |  relocate('kotlin', 'my.kotlin')
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("META-INF/", "META-INF/kotlin-stdlib.shadow.kotlin_module", *manifestEntries)
      getBytes("META-INF/kotlin-stdlib.shadow.kotlin_module").isNotNull()
    }
  }

  @Test
  fun manifestAppenderTransformer() {
    val one = buildJarOne {
      insert("foo/bar.txt", "bar")
    }
    projectScript.appendText(
      transform<ManifestAppenderTransformer>(
        dependenciesBlock = implementationFiles(one),
        transformerBlock =
          """
          |it.append('Name', 'org/foo/bar/')
          |it.append('Sealed', 'true')
          """
            .trimMargin(),
      )
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent("META-INF/MANIFEST.MF")
        .isEqualTo(
          """
          |Manifest-Version: 1.0
          |
          |Name: org/foo/bar/
          |Sealed: true
          |
          |"""
            .trimMargin()
            .crlfEolString
        )
    }
  }

  @Test
  fun xmlAppendingTransformer() {
    val xmlEntry = "META-INF/custom.xml"
    val one = buildJarOne {
      insert(
        xmlEntry,
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<root>
        |  <child id="1"/>
        |</root>
        """
          .trimMargin(),
      )
    }
    val two = buildJarTwo {
      insert(
        xmlEntry,
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<root>
        |  <child id="2"/>
        |</root>
        """
          .trimMargin(),
      )
    }
    projectScript.appendText(
      transform<XmlAppendingTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = "resource = '$xmlEntry'",
      )
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(xmlEntry)
        .isEqualTo(
          """
          |<?xml version="1.0" encoding="UTF-8"?>
          |<root>
          |  <child id="1" />
          |  <child id="2" />
          |</root>
          |"""
            .trimMargin()
        )
    }
  }

  private fun commonAssertions(
    mainAttributesBlock: JarAttribute.() -> Unit = {
      assertThat(getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("PASSED")
      assertThat(getValue(mainClassAttributeKey)).isEqualTo("my.Main")
      assertThat(getValue(NEW_ENTRY_ATTR_KEY)).isEqualTo("NEW")
    }
  ) {
    val mf = outputShadowedJar.use { it.manifest }
    assertThat(mf).isNotNull()
    mainAttributesBlock(mf.mainAttributes)
  }

  private companion object {
    const val NEW_ENTRY_ATTR_KEY = "New-Entry"
    const val TEST_ENTRY_ATTR_KEY = "Test-Entry"

    val MANIFEST_ATTRS =
      """
      |$jarTask {
      |  manifest {
      |    attributes '$mainClassAttributeKey': 'my.Main'
      |    attributes '$TEST_ENTRY_ATTR_KEY': 'FAILED'
      |  }
      |}
      |$shadowJarTask {
      |  manifest {
      |    attributes '$NEW_ENTRY_ATTR_KEY': 'NEW'
      |    attributes '$TEST_ENTRY_ATTR_KEY': 'PASSED'
      |  }
      |}
      """
        .trimMargin()
  }
}
