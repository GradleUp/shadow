package com.github.jengelman.gradle.plugins.shadow.executor

import com.github.jengelman.gradle.plugins.shadow.fixture.SnippetFixture
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner

class KotlinBuildExecutor(
  override val fixture: SnippetFixture,
  private val importExtractor: (String) -> List<String>,
) : SnippetExecutor {

  override fun execute(tempDir: Path, snippet: String) {
    tempDir.resolve("settings.gradle.kts").writeText(
      """
        dependencyResolutionManagement {
          repositories {
            mavenLocal()
            mavenCentral()
          }
        }
        include("api", "main")
      """.trimIndent(),
    )

    val apiScript = """
      plugins {
        java
        id("com.gradleup.shadow")
      }
    """.trimIndent()
    tempDir.addSubProject("api", apiScript)

    val (imports, snippetWithoutImports) = importExtractor(snippet)
    val mainScript = buildString {
      append(imports)
      append(System.lineSeparator())
      // All buildscript {} blocks must appear before any plugins {} blocks in the script.
      if (snippetWithoutImports.contains("buildscript {")) {
        append(snippetWithoutImports)
      } else {
        if (!snippetWithoutImports.contains("plugins {")) {
          append(fixture.pluginsBlock)
          append(System.lineSeparator())
        }
        append(snippetWithoutImports)
      }
      append(System.lineSeparator())
    }.trimIndent()
    tempDir.addSubProject("main", mainScript)

    try {
      GradleRunner.create()
        .withProjectDir(tempDir.toFile())
        .withPluginClasspath()
        .forwardOutput()
        .withArguments("--warning-mode=fail", "build")
        .build()
    } catch (t: Throwable) {
      throw RuntimeException("Failed to execute snippet:\n\n$mainScript", t)
    }
  }

  private fun Path.addSubProject(project: String, buildScriptText: String) {
    resolve(project)
      .createDirectory()
      .resolve("build.gradle.kts")
      .writeText(buildScriptText)
  }
}
