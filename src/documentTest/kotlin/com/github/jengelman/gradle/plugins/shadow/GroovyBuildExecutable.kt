package com.github.jengelman.gradle.plugins.shadow

class GroovyBuildExecutable(
  override val snippet: String,
  override val sourceLocation: String,
) : SnippetExecutable {
  override val buildScriptName: String = "build.gradle"

  override val assembleDependsOn: String =
    """
    |tasks.named('assemble') {
    |  dependsOn tasks.withType(Jar) // ShadowJar is a subtype of Jar.
    |}
    """
      .trimMargin()
}
