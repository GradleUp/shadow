package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScalaPluginTest : BasePluginTest() {
  @BeforeEach
  override fun setup() {
    super.setup()
    val projectBuildScript = getDefaultProjectBuildScript(
      plugin = "scala",
      withGroup = true,
      withVersion = true,
    )
    projectScriptPath.writeText(projectBuildScript)
  }

  @Test
  fun compatScala() {
    val mainClassEntry = writeClass(withImports = true, jvmLang = JvmLang.Scala)
    projectScriptPath.appendText(
      """
        dependencies {
          compileOnly 'org.scala-lang:scala-library:2.13.16'
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
        includeDirs = true,
      )
    }
  }
}
