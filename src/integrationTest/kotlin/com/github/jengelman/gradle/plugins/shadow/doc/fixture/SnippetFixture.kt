package com.github.jengelman.gradle.plugins.shadow.doc.fixture

interface SnippetFixture {
  val pre: String

  val post: String

  val offset: Int get() = pre.lines().dropLastWhile { it.isEmpty() }.size

  fun transform(text: String): String = text
}
