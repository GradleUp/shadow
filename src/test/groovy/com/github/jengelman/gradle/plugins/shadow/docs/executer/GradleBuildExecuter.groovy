package com.github.jengelman.gradle.plugins.shadow.docs.executer

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecuter
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.runner.GradleRunner

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
    void execute(File tempDir, TestCodeSnippet snippet) throws Exception {
        addSubProject(tempDir)
        File settings = new File(tempDir, "settings.gradle")
        settings.text = """
rootProject.name = 'shadowTest'
include 'api', 'main'
"""

        File mainDir = new File(tempDir, "main")
        mainDir.mkdirs()
        File buildFile = new File(mainDir, buildFile)


        List<String> importsAndSnippet = importExtractor.apply(snippet.getSnippet())

        String imports = importsAndSnippet.get(0)
        String snippetMinusImports = fixture.transform(importsAndSnippet.get(1))
        String fullSnippet = imports + fixture.pre() + snippetMinusImports + fixture.post()

        buildFile.text = replaceTokens(fullSnippet)

        GradleRunner runner = GradleRunner.create()
            .withGradleVersion(PluginSpecification.TEST_GRADLE_VERSION)
            .withProjectDir(tempDir)
            .withPluginClasspath()
            .forwardOutput()

        runner.withArguments(":main:build", "-m").build()

    }

    private static void addSubProject(File dir) {
        File api = new File(dir, "api")
        api.mkdirs()
        File build = new File(api, "build.gradle")
        build.text = """
plugins {
    id 'java'
    id 'com.gradleup.shadow'
}

repositories {
    mavenLocal()
    mavenCentral()
}
"""
    }

    private static String replaceTokens(String snippet) {
        return snippet.replaceAll("@version@", 'latest')
    }
}
