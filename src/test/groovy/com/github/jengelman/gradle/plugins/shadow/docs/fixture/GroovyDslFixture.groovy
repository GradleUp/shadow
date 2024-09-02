package com.github.jengelman.gradle.plugins.shadow.docs.fixture

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.GroovyScriptFixture

import java.util.function.Function

class GroovyDslFixture extends GroovyScriptFixture {

    @Override
    String pre() {
        """
plugins {
    id 'java'
    id 'com.gradleup.shadow'
    id 'application'
}

version = "1.0"
group = 'shadow'

repositories {
    mavenLocal()
    mavenCentral()
}
"""
    }

    static class ImportsExtractor implements Function<String, List<String>> {

        @Override
        List<String> apply(String snippet) {
            StringBuilder imports = new StringBuilder()
            StringBuilder scriptMinusImports = new StringBuilder()

            for (String line : snippet.split("\\n")) {
                StringBuilder target
                if (line.trim().startsWith("import ")) {
                    target = imports
                } else {
                    target = scriptMinusImports
                }

                target.append(line).append("\n")
            }

            return Arrays.asList(imports.toString(), scriptMinusImports.toString())
        }
    }
}
