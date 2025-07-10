package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.doesNotContain
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class IsolatedProjectsCompatibilityTest : BasePluginTest() {

  @Test
  fun applyToProjectWithIsolatedProjectsEnabled() {
    writeClientAndServerModules()
    path("gradle.properties").writeText("org.gradle.unsafe.isolated-projects=true")
    settingsScriptPath.writeText(
      """
        plugins {
          id("com.gradle.develocity") version "4.0.2"
        }
        develocity {
          buildScan {
            termsOfUseUrl = "https://gradle.com/terms-of-service"
            termsOfUseAgree = "yes"
          }
        }
        ${settingsScriptPath.readText()}
      """.trimIndent(),
    )
    val result = assertDoesNotThrow { run(serverShadowJarTask) }
    assertThat(result.output).doesNotContain("Configuration cache problems")
  }
}
