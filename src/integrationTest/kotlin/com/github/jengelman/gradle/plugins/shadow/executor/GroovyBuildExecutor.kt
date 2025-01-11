package com.github.jengelman.gradle.plugins.shadow.executor

import com.github.jengelman.gradle.plugins.shadow.fixture.SnippetFixture
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner

class GroovyBuildExecutor(
  override val fixture: SnippetFixture,
  private val importExtractor: (String) -> List<String>,
) : SnippetExecutor {

  override fun execute(tempDir: Path, snippet: String) {
    tempDir.resolve("settings.gradle").writeText(
      """
        dependencyResolutionManagement {
          repositories {
            mavenLocal()
            mavenCentral()
          }
        }
        include 'api', 'main'
      """.trimIndent(),
    )

    val apiScript = """
      plugins {
        id 'java'
        id 'com.gradleup.shadow'
      }
    """.trimIndent()
    tempDir.addSubProject("api", apiScript)

    val (imports, snippetWithoutImports) = importExtractor(snippet)
    val mainScript = buildString {
      append(imports)
      append(System.lineSeparator())
      append(fixture.pre)
      append(System.lineSeparator())
      append(snippetWithoutImports)
      append(System.lineSeparator())
    }.trimIndent()
    tempDir.addSubProject("main", mainScript)

    try {
      runner.withProjectDir(tempDir.toFile()).build()
    } catch (t: Throwable) {
      throw RuntimeException("Failed to execute snippet:\n\n$mainScript", t)
    }
  }

  private fun Path.addSubProject(project: String, buildScriptText: String) {
    resolve(project)
      .createDirectory()
      .resolve("build.gradle")
      .writeText(buildScriptText)
  }

  private companion object {
    val runner: GradleRunner get() = GradleRunner.create()
      .withPluginClasspath()
      .forwardOutput()
      .withArguments("--warning-mode=fail", "build")
  }
}
