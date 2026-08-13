package com.github.jengelman.gradle.plugins.shadow.snippet

class KotlinBuildExecutable(
  override val snippet: String,
  override val displayName: String,
  override val sourceLocation: String,
) : SnippetExecutable {
  override val buildScriptName: String = "build.gradle.kts"

  override val pluginsBlock: String =
    """
    |plugins {
    |  java
    |  id("com.gradleup.shadow")
    |}
    """
      .trimMargin()

  override val assembleDependsOn: String =
    """
    |tasks.named("assemble") {
    |  dependsOn(tasks.withType(Jar::class.java)) // ShadowJar is a subtype of Jar.
    |}
    """
      .trimMargin()
}
