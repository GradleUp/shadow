package com.github.jengelman.gradle.plugins.shadow.docs.fixture

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.GroovyScriptFixture
import java.util.function.Function

class GroovyDslFixture : GroovyScriptFixture() {

  override fun pre(): String {
    return """
      plugins {
        id("java")
        id("com.gradleup.shadow")
        id("application")
      }

      version = "1.0"
      group = "shadow"

      repositories {
        mavenLocal()
        mavenCentral()
      }
    """.trimIndent()
  }

  class ImportsExtractor : Function<String, List<String>> {
    override fun apply(snippet: String): List<String> {
      val imports = StringBuilder()
      val scriptMinusImports = StringBuilder()

      snippet.lines().forEach { line ->
        val target = if (line.trim().startsWith("import ")) imports else scriptMinusImports
        target.append(line).append("\n")
      }

      return listOf(imports.toString(), scriptMinusImports.toString())
    }
  }
}
