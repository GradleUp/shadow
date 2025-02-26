package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KmpPluginTest : BasePluginTest() {
  @BeforeEach
  override fun setup() {
    super.setup()
    val projectBuildScript = getDefaultProjectBuildScript(
      plugin = "org.jetbrains.kotlin.multiplatform",
      withGroup = true,
      withVersion = true,
    )
    projectScriptPath.writeText(projectBuildScript)
  }

  @Test
  fun compatKmpJvmTarget() {
    val mainClass = writeMainClass(sourceSet = "jvmMain", isJava = false)
    projectScriptPath.appendText(
      """
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
      containsEntries(
        mainClass,
        *entriesInAB,
      )
    }
  }
}
