package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets;

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.junit.DelegatingTestRunner;
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.junit.RunnerProvider;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;

import java.util.LinkedList;
import java.util.List;

@RunWith(DelegatingTestRunner.class)
abstract public class CodeSnippetTestCase implements RunnerProvider {

    protected abstract void addTests(CodeSnippetTests tests);

    public final List<Runner> getRunners() {
        List<Runner> runners = new LinkedList<>();
        CodeSnippetTests tests = new DefaultCodeSnippetTests(getClass(), runners);
        addTests(tests);
        return runners;
    }

}
