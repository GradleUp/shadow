package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture
import java.io.File
import java.util.function.Function
import org.gradle.testkit.runner.GradleRunner

class GradleBuildExecutor @JvmOverloads constructor(
  private val buildFile: String,
  override val fixture: SnippetFixture,
  private val importExtractor: Function<String, List<String>>,
  private val arguments: List<String> = listOf("build", "-m"),
) : SnippetExecutor {

  override fun execute(tempDir: File, snippet: TestCodeSnippet) {
    addSubProject(tempDir)
    File(tempDir, "settings.gradle").apply {
      writeText(
        """
          rootProject.name = 'shadowTest'
          include 'api', 'main'
        """.trimIndent(),
      )
    }

    val mainDir = File(tempDir, "main").apply { mkdirs() }
    val buildFile = File(mainDir, buildFile)

    val importsAndSnippet = importExtractor.apply(snippet.snippet)
    val imports = importsAndSnippet[0]
    val snippetMinusImports = fixture.transform(importsAndSnippet[1])
    val fullSnippet = imports + fixture.pre() + snippetMinusImports + fixture.post()

    buildFile.writeText(fullSnippet.replaceTokens())

    GradleRunner.create()
      .withProjectDir(tempDir)
      .withPluginClasspath()
      .forwardOutput()
      .withArguments(":main:build", "-m")
      .build()
  }

  private fun addSubProject(dir: File) {
    val api = File(dir, "api").apply { mkdirs() }
    File(api, "build.gradle").apply {
      writeText(
        """
          plugins {
            id 'java'
            id 'com.gradleup.shadow'
          }

          repositories {
            mavenLocal()
            mavenCentral()
          }
        """.trimIndent(),
      )
    }
  }

  private fun String.replaceTokens(): String {
    return replace("@version@", SHADOW_VERSION)
  }

  private companion object {
    // TODO: replace this with `PluginSpecification.SHADOW_VERSION` once it's migrated to Kotlin.
    private val SHADOW_VERSION = System.getProperty("shadowVersion")
  }
}
