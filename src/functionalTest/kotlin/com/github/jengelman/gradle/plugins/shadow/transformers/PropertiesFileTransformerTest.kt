package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class PropertiesFileTransformerTest : BaseTransformerTest() {

  @ParameterizedTest
  @EnumSource(MergeStrategy::class)
  fun mergePropertiesWithDifferentStrategies(strategy: MergeStrategy) {
    val one = buildJarOne {
      insert("META-INF/test.properties", "key1=one\nkey2=one")
    }
    val two = buildJarTwo {
      insert("META-INF/test.properties", "key2=two\nkey3=two")
    }
    projectScriptPath.appendText(
      transform<PropertiesFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          mergeStrategy = $mergeStrategyClassName.$strategy
          mergeSeparator = ";"
          paths = ["META-INF/test.properties"]
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val expected = when (strategy) {
      MergeStrategy.First -> arrayOf("key1=one", "key2=one", "key3=two")
      MergeStrategy.Latest -> arrayOf("key1=one", "key2=two", "key3=two")
      MergeStrategy.Append -> arrayOf("key1=one", "key2=one;two", "key3=two")
    }
    val content = outputShadowJar.use { it.getContent("META-INF/test.properties") }
    assertThat(content).contains(*expected)
  }

  @Test
  fun mergePropertiesWithKeyTransformer() {
    val one = buildJarOne {
      insert("META-INF/test.properties", "foo=bar")
    }
    val two = buildJarTwo {
      insert("META-INF/test.properties", "FOO=baz")
    }
    projectScriptPath.appendText(
      transform<PropertiesFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          mergeStrategy = $mergeStrategyClassName.Append
          keyTransformer = { key -> key.toUpperCase() }
          paths = ["META-INF/test.properties"]
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent("META-INF/test.properties") }
    assertThat(content).contains("FOO=bar,baz")
  }

  @Test
  fun mergePropertiesWithSpecifiedCharset() {
    val one = buildJarOne {
      insert("META-INF/utf8.properties", "foo=第一")
    }
    val two = buildJarTwo {
      insert("META-INF/utf8.properties", "foo=第二")
    }
    projectScriptPath.appendText(
      transform<PropertiesFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          mergeStrategy = $mergeStrategyClassName.Append
                charsetName = "utf-8"
                paths = ["META-INF/utf8.properties"]
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent("META-INF/utf8.properties") }
    assertThat(content).contains("foo=第一,第二")
  }

  @Test
  fun mergePropertiesWithMappings() {
    val one = buildJarOne {
      insert("META-INF/foo.properties", "foo=1")
      insert("META-INF/bar.properties", "bar=2")
    }
    val two = buildJarTwo {
      insert("META-INF/foo.properties", "foo=3")
      insert("META-INF/bar.properties", "bar=4")
    }
    projectScriptPath.appendText(
      transform<PropertiesFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          mappings = [
            "META-INF/foo.properties": ["mergeStrategy": "append", "mergeSeparator": ";"],
            "META-INF/bar.properties": ["mergeStrategy": "latest"]
          ]
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      getContent("META-INF/foo.properties").contains("foo=1;3")
      getContent("META-INF/bar.properties").contains("bar=4")
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/622",
    "https://github.com/GradleUp/shadow/issues/856",
  )
  @Test
  fun mergedPropertiesDontContainDateComment() {
    val one = buildJarOne {
      insert("META-INF/test.properties", "foo=one")
    }
    val two = buildJarTwo {
      insert("META-INF/test.properties", "foo=two")
    }
    projectScriptPath.appendText(
      transform<PropertiesFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          mergeStrategy = $mergeStrategyClassName.Append
          paths = ["META-INF/test.properties"]
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent("META-INF/test.properties") }
    assertThat(content.trimIndent()).isEqualTo(
      """
        #

        foo=one,two
      """.trimIndent(),
    )
  }

  private companion object {
    val mergeStrategyClassName = requireNotNull(MergeStrategy::class.java.canonicalName)
  }
}
