package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer;

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture;
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet;

public interface SnippetExecuter {

  SnippetFixture getFixture();

  void execute(TestCodeSnippet snippet) throws Exception;

}
