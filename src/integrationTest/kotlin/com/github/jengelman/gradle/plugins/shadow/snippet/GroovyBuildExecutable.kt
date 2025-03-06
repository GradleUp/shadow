package com.github.jengelman.gradle.plugins.shadow.snippet

class GroovyBuildExecutable(
  override val snippet: String,
  override val displayName: String,
  override val exceptionTransformer: (Throwable) -> Throwable,
) : SnippetExecutable() {

  override val lang: DslLang = DslLang.Groovy

  override val buildScriptName: String = "build.gradle"

  override val pluginsBlock: String = """
    plugins {
      id 'java'
      id 'com.gradleup.shadow'
    }
  """.trimIndent()
}
