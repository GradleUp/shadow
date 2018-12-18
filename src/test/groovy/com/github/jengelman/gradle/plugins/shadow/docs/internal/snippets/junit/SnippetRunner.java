package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.junit;

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

public class SnippetRunner extends Runner {

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
      snippet.getExecuter().execute(snippet);
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
