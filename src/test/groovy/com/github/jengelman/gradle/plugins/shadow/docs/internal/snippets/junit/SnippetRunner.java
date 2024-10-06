package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.junit;

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet;
import org.gradle.internal.impldep.org.junit.runner.Description;
import org.gradle.internal.impldep.org.junit.runner.Runner;
import org.gradle.internal.impldep.org.junit.runner.notification.Failure;
import org.gradle.internal.impldep.org.junit.runner.notification.RunNotifier;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class SnippetRunner extends Runner {

  @TempDir
  private Path tempDir;

  private final Description description;
  private final TestCodeSnippet snippet;

  public SnippetRunner(Class<?> testClass, TestCodeSnippet snippet) {
    this.description = Description.createTestDescription(testClass, snippet.getTestName());
    this.snippet = snippet;
  }

  @Override
  public Description getDescription() {
    return description;
  }

  @Override
  public void run(RunNotifier notifier) {
    Description description = getDescription();
    String filter = System.getProperty("filter");
    if (filter != null && !filter.equals(description.getMethodName())) {
      notifier.fireTestIgnored(description);
      return;
    }

    try {
      notifier.fireTestStarted(description);
      snippet.getExecuter().execute(tempDir.toFile(), snippet);
    } catch (Throwable t) {
      Throwable transform;
      try {
        transform = snippet.getExceptionTransformer().transform(t, snippet.getExecuter().getFixture().getOffset());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      notifier.fireTestFailure(new Failure(description, transform));
    } finally {
      notifier.fireTestFinished(description);
    }
  }

  @Override
  public int testCount() {
    return 1;
  }


}
