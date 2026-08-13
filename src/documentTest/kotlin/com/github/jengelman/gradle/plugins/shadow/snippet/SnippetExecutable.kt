package com.github.jengelman.gradle.plugins.shadow.snippet

import com.github.jengelman.gradle.plugins.shadow.testkit.assertNoDeprecationWarnings
import com.github.jengelman.gradle.plugins.shadow.testkit.commonGradleArgs
import com.github.jengelman.gradle.plugins.shadow.testkit.enableNoImplicitLookupInParentProjects
import com.github.jengelman.gradle.plugins.shadow.testkit.gradleRunner
import java.nio.file.Path
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import org.gradle.testkit.runner.UnexpectedBuildFailure

sealed class SnippetExecutable {
  abstract val lang: DslLang
  abstract val buildScriptName: String
  abstract val pluginsBlock: String
  abstract val assembleDependsOn: String

  abstract val snippet: String

  /** Unique name for the test, formatted as `publishing/README.md:10`. */
  abstract val displayName: String
  abstract val sourceLocation: String

  override fun toString(): String = displayName

  fun execute(projectRoot: Path) {
    try {
      executeSnippet(projectRoot, snippet)
    } catch (t: Throwable) {
      throw RuntimeException(
        "The error line in the doc is near $sourceLocation\n\n${t.message}",
        t,
      )
    }
  }

  private fun executeSnippet(projectRoot: Path, snippet: String) {
    projectRoot
      .resolve("settings.gradle")
      .writeText(
        """
        |gradle.beforeProject { p ->
        |  // Snippet version placeholders resolve to '+', so avoid frequent remote version checks.
        |  p.buildscript.configurations.configureEach {
        |    resolutionStrategy.cacheDynamicVersionsFor(30, java.util.concurrent.TimeUnit.DAYS)
        |  }
        |  p.configurations.configureEach {
        |    resolutionStrategy.cacheDynamicVersionsFor(30, java.util.concurrent.TimeUnit.DAYS)
        |  }
        |}
        |dependencyResolutionManagement {
        |  repositories {
        |    mavenLocal()
        |    mavenCentral()
        |  }
        |  versionCatalogs.create('libs') {
        |    library('log4j-core', 'org.apache.logging.log4j:log4j-core:2.11.1')
        |  }
        |}
        |include ':api', ':main'
        |rootProject.name = 'snippet'
        |$enableNoImplicitLookupInParentProjects
        |enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
        |enableFeaturePreview 'TYPESAFE_PROJECT_ACCESSORS'
        """
          .trimMargin()
      )

    val apiScript = buildString {
      appendLine(pluginsBlock)
      append(assembleDependsOn)
    }
    projectRoot.addSubProject("api", apiScript)

    val (imports, withoutImports) = importsExtractor(snippet)
    val mainScript = buildString {
      appendLine(imports)
      // All buildscript {} blocks must appear before any plugins {} blocks in the script.
      if (withoutImports.contains("buildscript {")) {
        appendLine(withoutImports)
      } else {
        if (!withoutImports.contains("plugins {")) {
          appendLine(pluginsBlock)
        }
        appendLine(withoutImports)
      }
    }
      .trimIndent()
    projectRoot.addSubProject("main", mainScript + assembleDependsOn)
    projectRoot.resolve("main/foo.jar").createFile().also {
      // Dummy JAR file to ensure the project can be built.
      JarOutputStream(it.outputStream()).use {}
    }
    projectRoot.resolve("main/bar.jar").createFile().also {
      // Dummy JAR file to ensure the project can be built.
      JarOutputStream(it.outputStream()).use {}
    }

    // Script-defined classes (e.g., inline custom ResourceTransformer) are not supported by
    // CC/IP because transient script classloaders cannot be serialized.
    val runnerArgs =
      if (withoutImports.contains("class ")) {
        commonGradleArgs.filterNot {
          it == "--configuration-cache" || it.contains("isolated-projects")
        }
      } else {
        commonGradleArgs.toList()
      }

    try {
      gradleRunner(projectDir = projectRoot, arguments = runnerArgs + "build")
        .build()
        .assertNoDeprecationWarnings()
    } catch (t: Throwable) {
      val buildOutput = (t as? UnexpectedBuildFailure)?.buildResult?.output
      val message = buildString {
        appendLine("--- Snippet ---")
        appendLine()
        appendLine(mainScript)
        if (!buildOutput.isNullOrBlank()) {
          appendLine()
          appendLine("--- Gradle Build Output ---")
          appendLine()
          appendLine(buildOutput.trim())
        }
      }
      throw RuntimeException(message, t)
    }
  }

  private fun Path.addSubProject(project: String, buildScriptText: String) {
    resolve(project).createDirectory().resolve(buildScriptName).writeText(buildScriptText)
  }

  private fun importsExtractor(snippet: String): Pair<String, String> {
    val imports = StringBuilder()
    val withoutImports = StringBuilder()

    snippet.lines().forEach { line ->
      val target = if (line.trim().startsWith("import ")) imports else withoutImports
      target.appendLine(line)
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
      sourceLocation: String,
    ): SnippetExecutable =
      when (lang) {
        DslLang.Groovy -> GroovyBuildExecutable(snippet, testName, sourceLocation)
        DslLang.Kotlin -> KotlinBuildExecutable(snippet, testName, sourceLocation)
      }
  }
}
