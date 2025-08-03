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

  override val assembleDependsOn: String = """
    tasks.named('assemble') {
      dependsOn tasks.withType(Jar) // ShadowJar is a subtype of Jar.
    }
  """.trimIndent()
}
