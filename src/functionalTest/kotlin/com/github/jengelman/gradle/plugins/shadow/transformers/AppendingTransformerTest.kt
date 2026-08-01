package com.github.jengelman.gradle.plugins.shadow.transformers

import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class AppendingTransformerTest : BaseTransformerTest() {
  @Test
  fun appendTestProperties() {
    val one = buildJarOne { insert(ENTRY_TEST_PROPERTIES, CONTENT_ONE) }
    val two = buildJarTwo { insert(ENTRY_TEST_PROPERTIES, CONTENT_TWO) }
    projectScript.appendText(
      """
      dependencies {
        ${implementationFiles(one, two)}
      }
      $shadowJarTask {
        append('$ENTRY_TEST_PROPERTIES')
      }
    """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)
  }
}
