package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets;

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.ExceptionTransformer;
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecuter;

public class TestCodeSnippet {

  private final String snippet;
  private final String className;
  private final String testName;
  private final SnippetExecuter executer;
  private final ExceptionTransformer exceptionTransformer;

  public TestCodeSnippet(String snippet, String className, String testName, SnippetExecuter executer, ExceptionTransformer exceptionTransformer) {
    this.snippet = snippet;
    this.className = className;
    this.testName = testName;
    this.executer = executer;
    this.exceptionTransformer = exceptionTransformer;
  }

  public String getSnippet() {
    return snippet;
  }

  public String getClassName() {
    return className;
  }

  public String getTestName() {
    return testName;
  }

  public ExceptionTransformer getExceptionTransformer() {
    return exceptionTransformer;
  }

  public SnippetExecuter getExecuter() {
    return executer;
  }
}
