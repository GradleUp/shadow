package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class KmpPluginTest : BasePluginTest() {
  @Test
  fun compatKmpJvmTarget() {
    projectScriptPath.appendText(
      """
        apply plugin: 'org.jetbrains.kotlin.multiplatform'
        kotlin {
          jvm()
          sourceSets {
            commonMain {
              dependencies {
                implementation 'my:b:1.0'
              }
            }
            jvmMain {
              dependencies {
                implementation 'my:a:1.0'
              }
            }
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(*entriesInAB)
    }
  }
}
