package com.github.jengelman.gradle.plugins.shadow.docs

import com.github.jengelman.gradle.plugins.shadow.docs.executer.GradleBuildExecuter
import com.github.jengelman.gradle.plugins.shadow.docs.executer.NoopExecuter
import com.github.jengelman.gradle.plugins.shadow.docs.extractor.ManualSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.docs.fixture.GroovyDslFixture
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecuter
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.GroovyScriptFixture
import com.google.common.base.StandardSystemProperty
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

class ManualCodeSnippetTests {
    @TempDir
    private Path tempDir

    public static final LinkedHashMap<String, SnippetExecuter> FIXTURES = [
            "groovy": new GradleBuildExecuter("build.gradle", new GroovyDslFixture(), new GroovyDslFixture.ImportsExtractor()),
            "groovy no-plugins": new GradleBuildExecuter("build.gradle", new GroovyScriptFixture(), new GroovyDslFixture.ImportsExtractor()),
            "groovy no-run": new NoopExecuter()
    ]

    @TestFactory
    List<DynamicTest> provideDynamicTests() {
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
