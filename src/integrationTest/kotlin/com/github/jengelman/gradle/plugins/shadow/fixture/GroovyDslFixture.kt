package com.github.jengelman.gradle.plugins.shadow.fixture

object GroovyDslFixture : SnippetFixture {

  override val pluginsBlock: String = """
    plugins {
      id 'java'
      id 'com.gradleup.shadow'
    }
  """.trimIndent()
}
