package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy
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
      """
            dependencies {
              ${implementationFiles(one, two)}
            }
            $shadowJar {
              transform(com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer) {
                mergeStrategy = com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy.$strategy
                mergeSeparator = ";"
                paths = ["META-INF/test.properties"]
              }
            }
      """.trimIndent(),
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
      """
            dependencies {
              ${implementationFiles(one, two)}
            }
            $shadowJar {
              transform(com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer) {
                mergeStrategy = com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy.Append
                keyTransformer = { key -> key.toUpperCase() }
                paths = ["META-INF/test.properties"]
              }
            }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent("META-INF/test.properties") }
    assertThat(content).contains("FOO=bar,baz")
  }

  @Test
  fun mergePropertiesWithMappings() {
    val one = buildJarOne {
      insert("META-INF/a.properties", "k=1")
      insert("META-INF/b.properties", "k=2")
    }
    val two = buildJarTwo {
      insert("META-INF/a.properties", "k=3")
      insert("META-INF/b.properties", "k=4")
    }
    projectScriptPath.appendText(
      """
            dependencies {
              ${implementationFiles(one, two)}
            }
            $shadowJar {
              transform(com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer) {
                mappings = [
                  "META-INF/a.properties": ["mergeStrategy": "append", "mergeSeparator": ":"],
                  "META-INF/b.properties": ["mergeStrategy": "latest"]
                ]
              }
            }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      getContent("META-INF/a.properties").isEqualTo("k=1:3")
      getContent("META-INF/b.properties").isEqualTo("k=4")
    }
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
      """
            dependencies {
              ${implementationFiles(one, two)}
            }
            $shadowJar {
              transform(com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer) {
                mergeStrategy = com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy.Append
                charsetName = "utf-8"
                paths = ["META-INF/utf8.properties"]
              }
            }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent("META-INF/utf8.properties") }
    assertThat(content).contains("foo=第一,第二")
  }
}
