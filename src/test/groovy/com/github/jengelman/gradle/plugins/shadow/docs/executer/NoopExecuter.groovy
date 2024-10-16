package com.github.jengelman.gradle.plugins.shadow.docs.executer

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecuter
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture

class NoopExecuter implements SnippetExecuter {

    @Override
    SnippetFixture getFixture() {
        return null
    }

    @Override
    void execute(File tempDir, TestCodeSnippet snippet) throws Exception {
        // noop
    }
}
