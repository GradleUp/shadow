package com.github.jengelman.gradle.plugins.shadow.doc.fixture

interface SnippetFixture {
  fun transform(text: String): String = text

  fun pre(): String = ""

  fun post(): String = ""

  val offset: Int get() = pre().lines().dropLastWhile { it.isEmpty() }.size
}
