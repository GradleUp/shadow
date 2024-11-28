package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture

abstract class SnippetFixture {
  fun transform(text: String): String = text

  open fun pre(): String = ""

  open fun post(): String = ""

  val offset: Int get() = pre().lines().dropLastWhile { it.isEmpty() }.size
}
