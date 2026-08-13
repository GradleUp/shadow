package com.github.jengelman.gradle.plugins.shadow.snippet

import com.github.jengelman.gradle.plugins.shadow.testkit.assertNoDeprecationWarnings
import com.github.jengelman.gradle.plugins.shadow.testkit.commonGradleArgs
import com.github.jengelman.gradle.plugins.shadow.testkit.enableNoImplicitLookupInParentProjects
import com.github.jengelman.gradle.plugins.shadow.testkit.gradleRunner
import java.nio.file.Path
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import org.gradle.testkit.runner.UnexpectedBuildFailure

sealed interface SnippetExecutable {
  val buildScriptName: String
  val pluginsBlock: String
  val assembleDependsOn: String
  val snippet: String
  /** Unique name for the test, formatted as `publishing/README.md:10`. */
  val displayName: String
  val sourceLocation: String

  fun execute(projectRoot: Path) {
    var generatedMainScript: String? = null
    var gradleBuildOutput: String? = null
    try {
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

      val (imports, withoutImports) = extractImports()
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
      generatedMainScript = mainScript
      projectRoot.addSubProject("main", mainScript + assembleDependsOn)
      listOf("foo.jar", "bar.jar").forEach { name ->
        // Dummy JAR file to ensure the project can be built.
        JarOutputStream(projectRoot.resolve("main/$name").outputStream()).use {}
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

      gradleRunner(projectDir = projectRoot, arguments = runnerArgs + "build")
        .build()
        .also { gradleBuildOutput = it.output }
        .assertNoDeprecationWarnings()
    } catch (t: Throwable) {
      val buildOutput = (t as? UnexpectedBuildFailure)?.buildResult?.output ?: gradleBuildOutput
      throw RuntimeException(
        buildString {
          append("The error line in the doc is near $sourceLocation")
          if (generatedMainScript != null) {
            appendLine()
            appendLine()
            appendLine("--- Snippet ---")
            appendLine()
            append(generatedMainScript)
          }
          if (!buildOutput.isNullOrBlank()) {
            appendLine()
            appendLine()
            appendLine("--- Gradle Build Output ---")
            appendLine()
            append(buildOutput.trim())
          } else if (!t.message.isNullOrBlank()) {
            appendLine()
            appendLine()
            append(t.message)
          }
        },
        t,
      )
    }
  }

  private fun Path.addSubProject(project: String, buildScriptText: String) {
    resolve(project).createDirectory().resolve(buildScriptName).writeText(buildScriptText)
  }

  private fun extractImports(): Pair<String, String> {
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
