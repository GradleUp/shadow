package com.github.jengelman.gradle.plugins.shadow.docs

import com.github.jengelman.gradle.plugins.shadow.docs.extractor.ManualSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.CodeSnippetTestCase
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.CodeSnippetTests
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecuter
import com.google.common.base.StandardSystemProperty

class ManualCodeSnippetTests extends CodeSnippetTestCase {

    public static final LinkedHashMap<String, SnippetExecuter> FIXTURES = [
//            "language-groovy groovy-chain-dsl": new GroovySnippetExecuter(true, new GroovyChainDslFixture()),
//            "language-groovy groovy-ratpack"  : new GroovySnippetExecuter(true, new GroovyRatpackDslNoRunFixture()),
//            "language-groovy groovy-handlers" : new GroovySnippetExecuter(true, new GroovyHandlersFixture()),
//            "language-groovy gradle"          : new GradleSnippetExecuter(new SnippetFixture()),
//            "language-groovy tested"          : new GroovySnippetExecuter(true, new GroovyScriptFixture()),
//            "language-java"                   : new JavaSnippetExecuter(new SnippetFixture()),
//            "language-java hello-world"       : new HelloWorldAppSnippetExecuter(new JavaSnippetExecuter(new SnippetFixture())),
//            "language-groovy hello-world"     : new HelloWorldAppSnippetExecuter(new GroovySnippetExecuter(true, new GroovyScriptRatpackDslFixture())),
//            "language-groovy hello-world-grab": new HelloWorldAppSnippetExecuter(new GroovySnippetExecuter(true, new GroovyScriptRatpackDslFixture() {
//                @Override
//                String transform(String text) {
//                    return text.readLines()[4..-1].join("\n")
//                }
//            }))
    ]

    @Override
    protected void addTests(CodeSnippetTests tests) {
        File cwd = new File(StandardSystemProperty.USER_DIR.value())
        File root
        if (new File(cwd, "ratpack-manual.gradle").exists()) {
            root = cwd.parentFile
        } else {
            root = cwd
        }

        def content = new File(root, "ratpack-manual/src/content/chapters")

        FIXTURES.each { selector, executer ->
            ManualSnippetExtractor.extract(content, selector, executer).each {
                tests.add(it)
            }
        }
    }

}