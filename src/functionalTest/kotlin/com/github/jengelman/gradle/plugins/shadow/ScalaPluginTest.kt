package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScalaPluginTest : BasePluginTest() {
  @BeforeEach
  override fun beforeEach() {
    super.beforeEach()
    projectScript.writeText(getDefaultProjectBuildScript(plugin = "scala"))
  }

  @Test
  fun compatScala() {
    val mainClassEntry = writeClass(withImports = true, jvmLang = JvmLang.Scala)
    projectScript.appendText(
      """
        dependencies {
          compileOnly 'org.scala-lang:scala-library:2.13.16'
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "my/",
        "my/Main$.class",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
    }
  }
}
