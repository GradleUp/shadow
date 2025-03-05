package com.github.jengelman.gradle.plugins.shadow.fixture

object KotlinDslFixture : SnippetFixture {

  override val pluginsBlock: String = """
    plugins {
      java
      id("com.gradleup.shadow")
    }
  """.trimIndent()
}
