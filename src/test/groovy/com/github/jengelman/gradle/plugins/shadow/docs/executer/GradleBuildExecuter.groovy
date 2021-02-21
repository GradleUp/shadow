package com.github.jengelman.gradle.plugins.shadow.docs.executer

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecuter
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder

import java.util.function.Function

class GradleBuildExecuter implements SnippetExecuter {

    private final SnippetFixture fixture
    private final String buildFile
    private final Function<String, List<String>> importExtractor

    private List<String> arguments = ["build", "-m"]

    GradleBuildExecuter(String buildFile, SnippetFixture fixture, Function<String, List<String>> importExtractor) {
        this.buildFile = buildFile
        this.fixture = fixture
        this.importExtractor = importExtractor
    }

    GradleBuildExecuter(String buildFile, List<String> arguments, SnippetFixture fixture, Function<String, List<String>> importExtractor) {
        this(buildFile, fixture, importExtractor)
        this.arguments = arguments
    }

    @Override
    SnippetFixture getFixture() {
        return fixture
    }

    @Override
    void execute(TestCodeSnippet snippet) throws Exception {
        TemporaryFolder tempDir = new TemporaryFolder()
        tempDir.create()
        File dir = tempDir.newFolder()

        addSubProject(dir)
        File settings = new File(dir, "settings.gradle")
        settings.text = """
rootProject.name = 'shadowTest'
include 'api', 'main'
"""

        File mainDir = new File(dir, "main")
        mainDir.mkdirs()
        File buildFile = new File(mainDir, buildFile)


        List<String> importsAndSnippet = importExtractor.apply(snippet.getSnippet())

        String imports = importsAndSnippet.get(0)
        String snippetMinusImports = fixture.transform(importsAndSnippet.get(1))
        String fullSnippet = imports + fixture.pre() + snippetMinusImports + fixture.post()

        buildFile.text = replaceTokens(fullSnippet)

        GradleRunner runner = GradleRunner.create().withProjectDir(dir).withPluginClasspath().forwardOutput()

        runner.withArguments(":main:build", "-m").build()

    }

    private void addSubProject(File dir) {
        File api = new File(dir, "api")
        api.mkdirs()
        File build = new File(api, "build.gradle")
        build.text = """
plugins {
    id 'java'
    id 'com.github.johnrengelman.shadow'
}

repositories {
    mavenLocal()
    mavenCentral()
}
"""
    }

    private static String replaceTokens(String snippet) {
        return snippet.replaceAll("@version@", PluginSpecification.SHADOW_VERSION + '-SNAPSHOT')
    }
}
