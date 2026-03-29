package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.invariantEolString
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class ProGuardTransformerTest : BaseTransformerTest() {
  @Test
  fun mergeProGuardFiles() {
    val proGuardEntry = "META-INF/proguard/app.pro"
    val content1 = "-keep class com.foo.Bar { *; }"
    val content2 = "-keep class com.foo.Baz { *; }"
    val one = buildJarOne { insert(proGuardEntry, content1) }
    val two = buildJarTwo { insert(proGuardEntry, content2) }
    val config = transform<ProGuardTransformer>(dependenciesBlock = implementationFiles(one, two))
    projectScript.appendText(config)

    runWithSuccess(shadowJarPath)

    val content = outputShadowedJar.use { it.getContent(proGuardEntry) }.invariantEolString
    assertThat(content).isEqualTo("$content1\n$content2")
  }

  @Test
  fun relocateProGuardFiles() {
    val proGuardEntry = "META-INF/proguard/app.pro"
    val content = "-keep class org.foo.Service { *; }\n-keep class org.foo.exclude.OtherService"
    val one = buildJarOne { insert(proGuardEntry, content) }
    val config =
      """
        dependencies {
          ${implementationFiles(one)}
        }
        $shadowJarTask {
          relocate('org.foo', 'borg.foo') {
            exclude 'org.foo.exclude.*'
          }
          transform(com.github.jengelman.gradle.plugins.shadow.transformers.ProGuardTransformer)
        }
      """
        .trimIndent()
    projectScript.appendText(config)

    runWithSuccess(shadowJarPath)

    val transformedContent =
      outputShadowedJar.use { it.getContent(proGuardEntry) }.invariantEolString
    assertThat(transformedContent)
      .isEqualTo("-keep class borg.foo.Service { *; }\n-keep class org.foo.exclude.OtherService")
  }
}
