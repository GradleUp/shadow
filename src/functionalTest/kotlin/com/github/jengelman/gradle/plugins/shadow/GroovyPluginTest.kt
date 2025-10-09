package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GroovyPluginTest : BasePluginTest() {
  @BeforeEach
  override fun beforeEach() {
    super.beforeEach()
    projectScript.writeText(getDefaultProjectBuildScript(plugin = "groovy"))
  }

  @Test
  fun compatGroovy() {
    val mainClassEntry = writeClass(withImports = true, jvmLang = JvmLang.Groovy)
    projectScript.appendText(
      """
        dependencies {
          compileOnly localGroovy()
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
    }
  }
}
