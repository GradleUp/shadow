package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class AppendingTransformerTest : BaseTransformerTest() {
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
        shadowJarBlock = fromJar(one, two),
        transformerBlock = """
          resource = '$ENTRY_TEST_PROPERTIES'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(ENTRY_TEST_PROPERTIES) }.trimIndent()
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
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

    val content = outputShadowJar.use { it.getContent(ENTRY_TEST_PROPERTIES) }.trimIndent()
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }
}
