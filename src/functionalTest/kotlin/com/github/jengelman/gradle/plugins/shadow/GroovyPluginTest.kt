package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GroovyPluginTest : BasePluginTest() {
  @BeforeEach
  override fun setup() {
    super.setup()
    val projectBuildScript = getDefaultProjectBuildScript(
      plugin = "groovy",
      withGroup = true,
      withVersion = true,
    )
    projectScriptPath.writeText(projectBuildScript)
  }

  @Test
  fun compatGroovy() {
    val mainClassEntry = writeClass(withImports = true, jvmLang = JvmLang.Groovy)
    projectScriptPath.appendText(
      """
        dependencies {
          compileOnly localGroovy()
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
      )
    }
  }
}
