package com.github.jengelman.gradle.plugins.shadow.docs.executer

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecutor
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture

class NoopExecutor implements SnippetExecutor {

    @Override
    SnippetFixture getFixture() {
        return null
    }

    @Override
    void execute(File tempDir, TestCodeSnippet snippet) throws Exception {
        // noop
    }
}
