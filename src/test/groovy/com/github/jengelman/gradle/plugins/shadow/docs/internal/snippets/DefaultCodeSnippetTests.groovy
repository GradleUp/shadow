package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.junit.SnippetRunner;
import org.junit.runner.Runner

class DefaultCodeSnippetTests implements CodeSnippetTests {

  private final Class<?> clazz
  private final List<Runner> runners

  DefaultCodeSnippetTests(Class<?> clazz, List<Runner> runners) {
    this.clazz = clazz
    this.runners = runners
  }

  void add(TestCodeSnippet snippet) {
    runners.add(new SnippetRunner(clazz, snippet))
  }

}
