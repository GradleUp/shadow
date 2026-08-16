package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import de.infix.testBalloon.framework.core.testSuite
import kotlin.io.path.appendText

val AppendingTransformerTests by testSuite {
  runTests(::AppendingTransformerTest)
}

private class AppendingTransformerTest : BaseTransformerTest() {
  fun appendTestProperties() {
    val one = buildJarOne { insert(ENTRY_TEST_PROPERTIES, CONTENT_ONE) }
    val two = buildJarTwo { insert(ENTRY_TEST_PROPERTIES, CONTENT_TWO) }
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(one, two)}
      |}
      |$shadowJarTask {
      |  append('$ENTRY_TEST_PROPERTIES')
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    val content = outputShadowedJar.use { it.getContent(ENTRY_TEST_PROPERTIES) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }
}
