package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class PreserveFirstFoundResourceTransformerTest : BaseTransformerTest() {
  @Test
  fun smokeTest() {
    val one = buildJarOne {
      insert("dup", "content")
      insert("foo", "content-foo")
    }
    val two = buildJarTwo {
      insert("dup", "content-different")
      insert("bar", "content-bar")
    }

    projectScript.appendText(
      transform<PreserveFirstFoundResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
        exclude("multiple-contents")
        """.trimIndent(),
      ),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("dup", "foo", "bar", "META-INF/", "META-INF/MANIFEST.MF")
      getContent("dup").isEqualTo("content")
      getContent("foo").isEqualTo("content-foo")
      getContent("bar").isEqualTo("content-bar")
    }
  }
}
