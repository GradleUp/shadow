package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import kotlin.io.path.deleteExisting
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class TransformerCachingTest : BaseCachingTest() {
  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingServiceFileTransformer() {
    writeMainClass()
    val assertions = {
      assertThat(outputShadowJar).useAll {
        containsEntries("shadow/Main.class")
      }
    }

    assertFirstExecutionSuccess()
    assertions()

    projectScriptPath.appendText(
      transform<ServiceFileTransformer>(
        transformerBlock = """
          path = 'META-INF/foo'
        """.trimIndent(),
      ),
    )
    assertFirstExecutionSuccess()
    assertions()

    assertExecutionsAreCachedAndUpToDate()
    assertions()

    val replaced = projectScriptPath.readText().replace("META-INF/foo", "META-INF/bar")
    projectScriptPath.writeText(replaced)

    assertFirstExecutionSuccess()
    assertions()

    assertExecutionsAreCachedAndUpToDate()
    assertions()
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingAppendingTransformer() {
    writeMainClass()
    path("src/main/resources/foo/bar.properties").writeText("foo=bar")
    val assertions = { name: String ->
      assertThat(outputShadowJar).useAll {
        containsEntries("shadow/Main.class", "foo/$name.properties")
        getContent("foo/$name.properties").isEqualTo("foo=$name")
      }
    }

    assertFirstExecutionSuccess()
    assertions("bar")

    projectScriptPath.appendText(
      transform<AppendingTransformer>(
        transformerBlock = """
          resource = 'foo/bar.properties'
        """.trimIndent(),
      ),
    )
    assertFirstExecutionSuccess()
    assertions("bar")

    assertExecutionsAreCachedAndUpToDate()
    assertions("bar")

    path("src/main/resources/foo/bar.properties").deleteExisting()
    path("src/main/resources/foo/baz.properties").writeText("foo=baz")
    val replaced = projectScriptPath.readText().replace("foo/bar.properties", "foo/baz.properties")
    projectScriptPath.writeText(replaced)

    assertFirstExecutionSuccess()
    assertions("baz")

    assertExecutionsAreCachedAndUpToDate()
    assertions("baz")
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingXmlAppendingTransformer() {
    writeMainClass()
    path("src/main/resources/foo/bar.xml").writeText("<foo>bar</foo>")
    val assertions = { name: String ->
      assertThat(outputShadowJar).useAll {
        containsEntries("shadow/Main.class", "foo/$name.xml")
        getContent("foo/$name.xml").contains("<foo>$name</foo>")
      }
    }

    assertFirstExecutionSuccess()
    assertions("bar")

    projectScriptPath.appendText(
      transform<XmlAppendingTransformer>(
        transformerBlock = """
          resource = 'foo/bar.xml'
        """.trimIndent(),
      ),
    )
    assertFirstExecutionSuccess()
    assertions("bar")

    assertExecutionsAreCachedAndUpToDate()
    assertions("bar")

    path("src/main/resources/foo/bar.xml").deleteExisting()
    path("src/main/resources/foo/baz.xml").writeText("<foo>baz</foo>")
    val replaced = projectScriptPath.readText().replace("foo/bar.xml", "foo/baz.xml")
    projectScriptPath.writeText(replaced)

    assertFirstExecutionSuccess()
    assertions("baz")

    assertExecutionsAreCachedAndUpToDate()
    assertions("baz")
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingGroovyExtensionModuleTransformer() {
    writeMainClass()
    val assertions = {
      assertThat(outputShadowJar).useAll {
        containsEntries("shadow/Main.class")
      }
    }

    assertFirstExecutionSuccess()
    assertions()

    projectScriptPath.appendText(
      transform<GroovyExtensionModuleTransformer>(),
    )
    assertFirstExecutionSuccess()
    assertions()

    assertExecutionsAreCachedAndUpToDate()
    assertions()
  }
}
