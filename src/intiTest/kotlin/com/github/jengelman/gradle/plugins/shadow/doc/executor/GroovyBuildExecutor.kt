package com.github.jengelman.gradle.plugins.shadow.doc.executor

import com.github.jengelman.gradle.plugins.shadow.doc.fixture.SnippetFixture
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner

class GroovyBuildExecutor(
  override val fixture: SnippetFixture,
  private val importExtractor: (String) -> List<String>,
  private val arguments: List<String> = listOf("build"),
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
    val projectBuildText = """
      plugins {
        id 'java'
        id 'com.gradleup.shadow'
      }
    """.trimIndent()
    tempDir.addSubProject("api", projectBuildText)

    val (imports, snippetWithoutImports) = importExtractor(snippet)
    val fullSnippet = buildString {
      append(imports)
      append(System.lineSeparator())
      append(fixture.pre)
      append(System.lineSeparator())
      append(snippetWithoutImports)
      append(System.lineSeparator())
      append(fixture.post)
      append(System.lineSeparator())
    }.trimIndent()

    tempDir.addSubProject("main", fullSnippet)

    val allArguments = listOf(
      "--warning-mode=fail",
      "--stacktrace",
    ) + arguments
    try {
      GradleRunner.create()
        .withProjectDir(tempDir.toFile())
        .withPluginClasspath()
        .forwardOutput()
        .withArguments(allArguments)
        .build()
    } catch (t: Throwable) {
      throw RuntimeException("Failed to execute snippet:\n\n$fullSnippet", t)
    }
  }

  private fun Path.addSubProject(project: String, buildScriptText: String) {
    resolve(project)
      .createDirectory()
      .resolve("build.gradle")
      .writeText(buildScriptText)
  }
}
