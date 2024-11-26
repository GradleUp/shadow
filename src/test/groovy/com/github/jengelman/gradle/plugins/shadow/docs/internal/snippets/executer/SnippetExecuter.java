package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer;

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet;
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture;

import java.io.File;

public interface SnippetExecuter {

  SnippetFixture getFixture();

  void execute(File tempDir, TestCodeSnippet snippet) throws Exception;

}
