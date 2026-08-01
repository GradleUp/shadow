package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AppendingTransformerTest : BaseTransformerTest() {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun appendTestProperties(shortSyntax: Boolean) {
    val one = buildJarOne { insert(ENTRY_TEST_PROPERTIES, CONTENT_ONE) }
    val two = buildJarTwo { insert(ENTRY_TEST_PROPERTIES, CONTENT_TWO) }
    val config =
      if (shortSyntax) {
        """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJarTask {
          append('$ENTRY_TEST_PROPERTIES')
        }
      """
          .trimIndent()
      } else {
        transform<AppendingTransformer>(
          dependenciesBlock = implementationFiles(one, two),
          transformerBlock =
            """
          resource = '$ENTRY_TEST_PROPERTIES'
        """
              .trimIndent(),
        )
      }
    projectScript.appendText(config)

    runWithSuccess(shadowJarPath)

    val content = outputShadowedJar.use { it.getContent(ENTRY_TEST_PROPERTIES) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }
}
