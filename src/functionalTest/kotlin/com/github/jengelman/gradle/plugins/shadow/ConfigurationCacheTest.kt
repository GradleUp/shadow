package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import kotlin.io.path.appendText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigurationCacheTest : BasePluginTest() {

  @BeforeEach
  override fun setup() {
    super.setup()
  }

  @Test
  fun includeFilesInTaskOutputDirectory() {
    // Create a build that has a task with jars in the output directory
    projectScriptPath.appendText(
      """
      def createJars = tasks.register("createJars") {
        def artifactAJar = file("$artifactAJar")
        def artifactBJar = file("$artifactBJar")
        inputs.files(artifactAJar, artifactBJar)
        def outputDir = file("${'$'}{buildDir}/jars")
        outputs.dir(outputDir)
        doLast {
          artifactAJar.withInputStream { input ->
              new File(outputDir, "jarA.jar").withOutputStream { output ->
                  output << input
              }
          }
          artifactBJar.withInputStream { input ->
              new File(outputDir, "jarB.jar").withOutputStream { output ->
                  output << input
              }
          }
        }
      }

      tasks.shadowJar {
        includedDependencies.from(files(createJars).asFileTree)
      }
      """.trimIndent(),
    )

    run(shadowJarTask, "--configuration-cache")

    assertThat(outputShadowJar).useAll {
      containsOnly(*entriesInAB, *manifestEntries)
    }
  }
}
