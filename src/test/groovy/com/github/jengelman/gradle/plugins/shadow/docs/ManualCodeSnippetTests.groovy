package com.github.jengelman.gradle.plugins.shadow.docs

import com.github.jengelman.gradle.plugins.shadow.docs.executer.GradleBuildExecutor
import com.github.jengelman.gradle.plugins.shadow.docs.executer.NoopExecutor
import com.github.jengelman.gradle.plugins.shadow.docs.extractor.ManualSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.docs.fixture.GroovyDslFixture
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecutor
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.GroovyScriptFixture
import com.google.common.base.StandardSystemProperty
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

class ManualCodeSnippetTests {
    public static final LinkedHashMap<String, SnippetExecutor> FIXTURES = [
        "groovy"           : new GradleBuildExecutor("build.gradle", new GroovyDslFixture(), new GroovyDslFixture.ImportsExtractor()),
        "groovy no-plugins": new GradleBuildExecutor("build.gradle", new GroovyScriptFixture(), new GroovyDslFixture.ImportsExtractor()),
        "groovy no-run"    : new NoopExecutor()
    ]

    @TestFactory
    List<DynamicTest> provideDynamicTests(@TempDir Path tempDir) {
        File cwd = new File(StandardSystemProperty.USER_DIR.value())
        def content = new File(cwd, "src/docs")
        List<TestCodeSnippet> snippets = []

        FIXTURES.each { selector, executer ->
            ManualSnippetExtractor.extract(tempDir, content, selector, executer).each {
                snippets.add(it)
            }
        }
        return snippets.collect {
            DynamicTest.dynamicTest(it.testName, it)
        }
    }
}
