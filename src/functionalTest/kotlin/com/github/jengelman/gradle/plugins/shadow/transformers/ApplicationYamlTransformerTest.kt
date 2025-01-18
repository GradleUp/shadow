package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.ApplicationYamlTransformer.Companion.APPLICATION_YML
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class ApplicationYamlTransformerTest : BaseTransformerTest() {
  @Test
  fun canTransformApplicationYaml() {
    val one = buildJarOne {
      insert("resources/$APPLICATION_YML", CONTENT_ONE + System.lineSeparator())
      insert("resources/custom-config/$APPLICATION_YML", CONTENT_TWO)
    }
    val two = buildJarTwo {
      insert("resources/$APPLICATION_YML", CONTENT_TWO + System.lineSeparator())
      insert("resources/custom-config/$APPLICATION_YML", CONTENT_THREE)
    }

    projectScriptPath.appendText(
      transform<ApplicationYamlTransformer>(
        shadowJarBlock = fromJar(one, two),
      ),
    )

    run(shadowJarTask)

    val content1 = outputShadowJar.use { it.getContent("resources/$APPLICATION_YML") }.trimIndent()
    assertThat(content1).isEqualTo(
      """
      $CONTENT_ONE
      ---
      $CONTENT_TWO
      """.trimIndent(),
    )
    val content2 = outputShadowJar.use { it.getContent("resources/custom-config/$APPLICATION_YML") }
      .trimIndent()
    assertThat(content2).isEqualTo(
      """
      $CONTENT_TWO
      ---
      $CONTENT_THREE
      """.trimIndent(),
    )
  }
}
