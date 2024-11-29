package com.github.jengelman.gradle.plugins.shadow.doc.extractor

import com.github.jengelman.gradle.plugins.shadow.doc.executable.TestCodeExecutable
import com.github.jengelman.gradle.plugins.shadow.doc.fixture.SnippetFixture
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language

class GroovyBuildExecutor(
  override val fixture: SnippetFixture,
  private val importExtractor: (String) -> List<String>,
  private val arguments: Array<String> = arrayOf("build", "-m"),
) : SnippetExecutor {

  override fun execute(tempDir: Path, snippet: TestCodeExecutable) {
    tempDir.resolve("settings.gradle").writeText(settingsBuildText)
    tempDir.addSubProject("api", projectBuildText)

    val importsAndSnippet = importExtractor(snippet.snippet)
    val imports = importsAndSnippet.first()
    val snippetMinusImports = fixture.transform(importsAndSnippet[1])
    val fullSnippet = imports + fixture.pre + snippetMinusImports + fixture.post

    tempDir.addSubProject("main", fullSnippet)

    GradleRunner.create()
      .withProjectDir(tempDir.toFile())
      .withPluginClasspath()
      .forwardOutput()
      .withArguments(*arguments)
      .build()
  }

  private fun Path.addSubProject(project: String, buildScriptText: String) {
    resolve(project)
      .createDirectory()
      .resolve("build.gradle")
      .writeText(buildScriptText)
  }

  private companion object {
    @Language("Groovy")
    private val settingsBuildText = """
      rootProject.name = 'shadowTest'
      include 'api', 'main'
    """.trimIndent()

    @Language("Groovy")
    private val projectBuildText = """
      plugins {
        id 'java'
        id 'com.gradleup.shadow'
      }
      repositories {
        mavenLocal()
        mavenCentral()
      }
    """.trimIndent()
  }
}
