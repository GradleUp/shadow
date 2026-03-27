package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProGuardTransformerTest : BaseTransformerTest() {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun mergeProGuardFiles(shortSyntax: Boolean) {
    val proGuardEntry = "META-INF/proguard/app.pro"
    val content1 = "-keep class com.foo.Bar { *; }"
    val content2 = "-keep class com.foo.Baz { *; }"
    val one = buildJarOne { insert(proGuardEntry, content1) }
    val two = buildJarTwo { insert(proGuardEntry, content2) }
    val config =
      if (shortSyntax) {
        """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJarTask {
          mergeProGuardFiles()
        }
      """
          .trimIndent()
      } else {
        transform<ProGuardTransformer>(dependenciesBlock = implementationFiles(one, two))
      }
    projectScript.appendText(config)

    runWithSuccess(shadowJarPath)

    val content = outputShadowedJar.use { it.getContent(proGuardEntry) }
    assertThat(content).isEqualTo("$content1\n$content2")
  }
}
