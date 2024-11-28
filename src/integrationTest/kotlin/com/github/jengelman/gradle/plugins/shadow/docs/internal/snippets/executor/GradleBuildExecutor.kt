package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner

class GradleBuildExecutor(
  private val buildFile: String,
  override val fixture: SnippetFixture,
  private val importExtractor: (String) -> List<String>,
  private val arguments: List<String> = listOf("build", "-m"),
) : SnippetExecutor {

  override fun execute(tempDir: Path, snippet: TestCodeSnippet) {
    addSubProject(tempDir)
    tempDir.resolve("settings.gradle").writeText(
      """
        rootProject.name = 'shadowTest'
        include 'api', 'main'
      """.trimIndent(),
    )

    val mainDir = tempDir.resolve("main").apply { createDirectory() }
    val buildFile = mainDir.resolve(buildFile)

    val importsAndSnippet = importExtractor(snippet.snippet)
    val imports = importsAndSnippet[0]
    val snippetMinusImports = fixture.transform(importsAndSnippet[1])
    val fullSnippet = imports + fixture.pre() + snippetMinusImports + fixture.post()

    buildFile.writeText(fullSnippet.replaceTokens())

    GradleRunner.create()
      .withProjectDir(tempDir.toFile())
      .withPluginClasspath()
      .forwardOutput()
      .withArguments(":main:build", "-m")
      .build()
  }

  private fun addSubProject(dir: Path) {
    val api = dir.resolve("api").apply { createDirectory() }
    api.resolve("build.gradle").writeText(
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

  private fun String.replaceTokens(): String {
    return replace("@version@", SHADOW_VERSION)
  }

  private companion object {
    private val SHADOW_VERSION = System.getProperty("shadowVersion")
  }
}
