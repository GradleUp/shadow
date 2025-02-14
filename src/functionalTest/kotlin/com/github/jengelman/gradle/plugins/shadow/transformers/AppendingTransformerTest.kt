package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.BooleanParameterizedTest
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText

class AppendingTransformerTest : BaseTransformerTest() {
  @BooleanParameterizedTest
  fun appendTestProperties(shortSyntax: Boolean) {
    val one = buildJarOne {
      insert(ENTRY_TEST_PROPERTIES, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_TEST_PROPERTIES, CONTENT_TWO)
    }
    val config = if (shortSyntax) {
      """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJar {
          append('$ENTRY_TEST_PROPERTIES')
        }
      """.trimIndent()
    } else {
      transform<AppendingTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          resource = '$ENTRY_TEST_PROPERTIES'
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(config)

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(ENTRY_TEST_PROPERTIES) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }

  @BooleanParameterizedTest
  fun appendApplicationYaml(shortSyntax: Boolean) {
    val one = buildJarOne {
      insert("resources/$APPLICATION_YML_FILE", CONTENT_ONE)
      insert("resources/config/$APPLICATION_YML_FILE", CONTENT_TWO)
    }
    val two = buildJarTwo {
      insert("resources/$APPLICATION_YML_FILE", CONTENT_TWO)
      insert("resources/config/$APPLICATION_YML_FILE", CONTENT_THREE)
    }
    val config = if (shortSyntax) {
      """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJar {
          append('resources/$APPLICATION_YML_FILE', '$APPLICATION_YML_SEPARATOR')
          append('resources/config/$APPLICATION_YML_FILE', '$APPLICATION_YML_SEPARATOR')
        }
      """.trimIndent()
    } else {
      val block1 = transform<AppendingTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          resource = 'resources/$APPLICATION_YML_FILE'
          separator = '$APPLICATION_YML_SEPARATOR'
        """.trimIndent(),
      )
      val block2 = transform<AppendingTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          resource = 'resources/config/$APPLICATION_YML_FILE'
          separator = '$APPLICATION_YML_SEPARATOR'
        """.trimIndent(),
      )
      block1 + System.lineSeparator() + block2
    }

    projectScriptPath.appendText(config)

    run(shadowJarTask)

    val content1 = outputShadowJar.use { it.getContent("resources/$APPLICATION_YML_FILE") }
    assertThat(content1).isEqualTo(
      """
      $CONTENT_ONE
      ---
      $CONTENT_TWO
      """.trimIndent(),
    )
    val content2 = outputShadowJar.use { it.getContent("resources/config/$APPLICATION_YML_FILE") }
    assertThat(content2).isEqualTo(
      """
      $CONTENT_TWO
      ---
      $CONTENT_THREE
      """.trimIndent(),
    )
  }

  private companion object {
    const val APPLICATION_YML_FILE = "application.yml"
    const val APPLICATION_YML_SEPARATOR = "\\n---\\n"
  }
}
