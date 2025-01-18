package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import java.nio.file.Path
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class AppendingTransformerTest : BaseTransformerTest() {
  @Test
  fun appendTestProperties() {
    val one = buildJarOne {
      insert(ENTRY_TEST_PROPERTIES, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_TEST_PROPERTIES, CONTENT_TWO)
    }
    projectScriptPath.appendText(
      transform<AppendingTransformer>(
        shadowJarBlock = fromJar(one, two),
        transformerBlock = """
          resource = '$ENTRY_TEST_PROPERTIES'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(ENTRY_TEST_PROPERTIES) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }

  @Test
  fun appendTestPropertiesShortSyntax() {
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

    val content = outputShadowJar.use { it.getContent(ENTRY_TEST_PROPERTIES) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }

  @Test
  fun appendApplicationYaml() {
    val (one, two) = writeApplicationYamlJars()

    projectScriptPath.appendText(
      transform<AppendingTransformer>(
        shadowJarBlock = fromJar(one, two),
        transformerBlock = """
          resource = 'resources/$APPLICATION_YML_FILE'
          separator = '$APPLICATION_YML_SEPARATOR'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent("resources/$APPLICATION_YML_FILE") }
    assertThat(content).isEqualTo(
      """
      $CONTENT_ONE
      ---
      $CONTENT_TWO
      """.trimIndent(),
    )
  }

  @Test
  fun appendApplicationYamlShortSyntax() {
    val (one, two) = writeApplicationYamlJars()

    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(one, two)}
          append('resources/$APPLICATION_YML_FILE', '$APPLICATION_YML_SEPARATOR')
          append('resources/config/$APPLICATION_YML_FILE', '$APPLICATION_YML_SEPARATOR')
        }
      """.trimIndent(),
    )

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

  private fun writeApplicationYamlJars(): Pair<Path, Path> {
    val one = buildJarOne {
      insert("resources/$APPLICATION_YML_FILE", CONTENT_ONE)
      insert("resources/config/$APPLICATION_YML_FILE", CONTENT_TWO)
    }
    val two = buildJarTwo {
      insert("resources/$APPLICATION_YML_FILE", CONTENT_TWO)
      insert("resources/config/$APPLICATION_YML_FILE", CONTENT_THREE)
    }
    return one to two
  }

  private companion object {
    const val APPLICATION_YML_FILE = "application.yml"
    const val APPLICATION_YML_SEPARATOR = "\\n---\\n"
  }
}
