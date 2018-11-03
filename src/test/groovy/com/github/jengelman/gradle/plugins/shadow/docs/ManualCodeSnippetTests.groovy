package com.github.jengelman.gradle.plugins.shadow.docs

import com.github.jengelman.gradle.plugins.shadow.docs.executer.GradleBuildExecuter
import com.github.jengelman.gradle.plugins.shadow.docs.executer.NoopExecuter
import com.github.jengelman.gradle.plugins.shadow.docs.extractor.ManualSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.docs.fixture.GroovyDslFixture
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.CodeSnippetTestCase
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.CodeSnippetTests
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecuter
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.GroovyScriptFixture
import com.google.common.base.StandardSystemProperty

class ManualCodeSnippetTests extends CodeSnippetTestCase {

    public static final LinkedHashMap<String, SnippetExecuter> FIXTURES = [
            "groovy": new GradleBuildExecuter("build.gradle", new GroovyDslFixture(), new GroovyDslFixture.ImportsExtractor()),
            "groovy no-plugins": new GradleBuildExecuter("build.gradle", new GroovyScriptFixture(), new GroovyDslFixture.ImportsExtractor()),
            "groovy no-run": new NoopExecuter()
    ]

    @Override
    protected void addTests(CodeSnippetTests tests) {
        File cwd = new File(StandardSystemProperty.USER_DIR.value())

        def content = new File(cwd, "src/docs")

        FIXTURES.each { selector, executer ->
            ManualSnippetExtractor.extract(content, selector, executer).each {
                tests.add(it)
            }
        }
    }

}