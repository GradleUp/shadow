package com.github.jengelman.gradle.plugins.shadow.doc.fixture

interface SnippetFixture {
  val pre: String

  val post: String

  fun transform(text: String): String = text
}
