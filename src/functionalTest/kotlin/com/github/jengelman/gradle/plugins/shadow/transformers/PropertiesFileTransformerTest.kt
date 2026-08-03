package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import kotlin.io.path.appendText
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class PropertiesFileTransformerTest : BaseTransformerTest() {

  @Test
  fun configureComplexTransformerProperties() {
    val propertiesEntry = "META-INF/test.properties"
    val one = buildJarOne {
      insert(propertiesEntry, "foo=第一")
      insert("META-INF/LICENSE", "license one")
    }
    val two = buildJarTwo {
      insert(propertiesEntry, "foo=第二")
      insert("META-INF/LICENSE", "license two")
    }
    val artifactLicense = path("my-license").apply { writeText("artifact license text") }

    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJarTask {
          transform(${PropertiesFileTransformer::class.java.name}) {
            mappings = [
              '$propertiesEntry': ['mergeStrategy': 'append', 'mergeSeparator': ';']
            ]
            charsetName = 'utf-8'
            keyTransformer = { key -> key.toUpperCase() }
          }
          transform(${MergeLicenseResourceTransformer::class.java.name}) {
            outputPath = 'MY_LICENSE'
            artifactLicense = file('${artifactLicense.invariantSeparatorsPathString}')
            firstSeparator = '####'
            separator = '----'
          }
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(propertiesEntry).contains("FOO=第一;第二")
      getContent("MY_LICENSE")
        .isEqualTo(
          """
          SPDX-License-Identifier: Apache-2.0
          artifact license text
          ####
          license one
          ----
          license two
          """
            .trimIndent()
        )
    }
  }
}
