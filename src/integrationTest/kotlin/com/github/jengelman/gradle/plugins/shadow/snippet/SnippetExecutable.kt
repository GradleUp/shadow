package com.github.jengelman.gradle.plugins.shadow.snippet

import java.lang.System.lineSeparator
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.function.Executable

sealed class SnippetExecutable : Executable {
  abstract val lang: DslLang
  abstract val buildScriptName: String
  abstract val pluginsBlock: String

  abstract val snippet: String
  abstract val displayName: String
  abstract val exceptionTransformer: (Throwable) -> Throwable

  lateinit var tempDir: Path

  override fun execute() {
    try {
      execute(tempDir, snippet)
    } catch (t: Throwable) {
      throw exceptionTransformer(t)
    }
  }

  private fun execute(projectRoot: Path, snippet: String) {
    projectRoot.resolve("settings.gradle").writeText(
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
    projectRoot.addSubProject("api", pluginsBlock)

    val (imports, withoutImports) = importsExtractor(snippet)
    val mainScript = buildString {
      append(imports)
      append(lineSeparator())
      // All buildscript {} blocks must appear before any plugins {} blocks in the script.
      if (withoutImports.contains("buildscript {")) {
        append(withoutImports)
      } else {
        if (!withoutImports.contains("plugins {")) {
          append(pluginsBlock)
          append(lineSeparator())
        }
        append(withoutImports)
      }
      append(lineSeparator())
    }.trimIndent()
    projectRoot.addSubProject("main", mainScript)

    try {
      GradleRunner.create()
        .withProjectDir(projectRoot.toFile())
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
      .resolve(buildScriptName)
      .writeText(buildScriptText)
  }

  private fun importsExtractor(snippet: String): Pair<String, String> {
    val imports = StringBuilder()
    val withoutImports = StringBuilder()

    snippet.lines().forEach { line ->
      val target = if (line.trim().startsWith("import ")) imports else withoutImports
      target.append(line).append(lineSeparator())
    }

    return imports.toString() to
      // Replace the version placeholders.
      withoutImports.toString().replace("<version>", "+")
  }

  companion object {
    fun create(
      lang: DslLang,
      snippet: String,
      testName: String,
      exceptionTransformer: (Throwable) -> Throwable,
    ): SnippetExecutable = when (lang) {
      DslLang.Groovy -> GroovyBuildExecutable(snippet, testName, exceptionTransformer)
//      DslLang.Kotlin -> KotlinBuildExecutable(snippet, testName, exceptionTransformer)
    }
  }
}
