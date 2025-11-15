package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.containsExactlyInAnyOrder
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import kotlin.text.lines
import org.junit.jupiter.api.Test

class MergeLicenseResourceTransformerTest : BaseTransformerTest() {

  @Test
  fun twoLicenses() {
    val one = buildJarOne {
      insert("META-INF/LICENSE", "license one")
    }
    val two = buildJarTwo {
      insert("META-INF/LICENSE", "license two")
    }

    val artifactLicense = projectRoot.resolve("my-license")
    artifactLicense.writeText("artifact license text")

    projectScript.appendText(
      transform<MergeLicenseResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          outputPath = 'MY_LICENSE'
          artifactLicense = file('my-license')
          firstSeparator = '####'
          separator = '----'
        """.trimIndent(),
      ),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsExactlyInAnyOrder(
        "MY_LICENSE",
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
      getContent("MY_LICENSE").given {
        assertThat(it.lines()).isEqualTo(
          listOf(
            "artifact license text",
            "####",
            "license one",
            "----",
            "license two",
          ),
        )
      }
    }
  }
}
