package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class PropertiesFileTransformerTest : BaseTransformerTest() {

  @Test
  fun mergePropertiesWithKeyTransformer() {
    val one = buildJarOne { insert("META-INF/test.properties", "foo=bar") }
    val two = buildJarTwo { insert("META-INF/test.properties", "FOO=baz") }
    projectScript.appendText(
      transform<PropertiesFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock =
          """
          mergeStrategy = ${MergeStrategy::class.java.canonicalName}.Append
          keyTransformer = { key -> key.toUpperCase() }
          paths = ["META-INF/test.properties"]
        """
            .trimIndent(),
      )
    )

    runWithSuccess(shadowJarPath)

    val content = outputShadowedJar.use { it.getContent("META-INF/test.properties") }
    assertThat(content).contains("FOO=bar,baz")
  }
}
