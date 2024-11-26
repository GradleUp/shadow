package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets;

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.ExceptionTransformer;
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecuter;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Path;

public class TestCodeSnippet implements Executable {

  private final Path tempDir;
  private final String snippet;
  private final String testName;
  private final SnippetExecuter executer;
  private final ExceptionTransformer exceptionTransformer;

  public TestCodeSnippet(Path tempDir, String snippet, String testName, SnippetExecuter executer, ExceptionTransformer exceptionTransformer) {
    this.tempDir = tempDir;
    this.snippet = snippet;
    this.testName = testName;
    this.executer = executer;
    this.exceptionTransformer = exceptionTransformer;
  }

  public String getSnippet() {
    return snippet;
  }

  public String getTestName() {
    return testName;
  }

  public void execute() throws Throwable {
    try {
      executer.execute(tempDir.toFile(), this);
    } catch (Throwable t) {
      throw exceptionTransformer.transform(t, executer.getFixture().getOffset());
    }
  }
}
