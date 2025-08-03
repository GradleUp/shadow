package com.github.jengelman.gradle.plugins.shadow.snippet

class KotlinBuildExecutable(
  override val snippet: String,
  override val displayName: String,
  override val exceptionTransformer: (Throwable) -> Throwable,
) : SnippetExecutable() {

  override val lang: DslLang = DslLang.Kotlin

  override val buildScriptName: String = "build.gradle.kts"

  override val pluginsBlock: String = """
    plugins {
      java
      id("com.gradleup.shadow")
    }
  """.trimIndent()

  override val assembleDependsOn: String = """
    tasks.named("assemble") {
      dependsOn(tasks.withType(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class.java))
    }
  """.trimIndent()
}
