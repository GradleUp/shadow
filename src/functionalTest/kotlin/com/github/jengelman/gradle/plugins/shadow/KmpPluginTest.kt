package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
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
    val mainClassEntry = "my.MainKt"
    val mainClassPath = writeClass(sourceSet = "jvmMain", isJava = false)
    projectScriptPath.appendText(
      """
        kotlin {
          jvm().mainRun {
            mainClass = '$mainClassEntry'
          }
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
        mainClassEntry,
        *entriesInAB,
      )
      getMainAttr(mainClassAttributeKey).isEqualTo(mainClassName)
    }
  }
}
