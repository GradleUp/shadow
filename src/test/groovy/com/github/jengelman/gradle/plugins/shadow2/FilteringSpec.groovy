package com.github.jengelman.gradle.plugins.shadow2

import com.github.jengelman.gradle.plugins.shadow.Shadow2Plugin
import com.github.jengelman.gradle.plugins.shadow2.util.AppendableMavenFileRepository
import com.github.jengelman.gradle.plugins.shadow2.util.PluginSpecification
import org.gradle.testkit.functional.ExecutionResult

class FilteringSpec extends PluginSpecification {

    AppendableMavenFileRepository repo

    def setup() {
        repo = repo()

        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()
        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        buildFile << """
apply plugin: ${Shadow2Plugin.name}

repositories { maven { url "${repo.uri}" } }
dependencies {
    compile 'shadow:a:1.0'
    compile 'shadow:b:1.0'
}

shadowJar {
    baseName = 'shadow'
    classifier = null
}
"""
    }

    def 'include all dependencies'() {
        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties', 'a2.properties', 'b.properties'])
    }

    def 'exclude a dependency by maven coordinates'() {
        given:
        buildFile << '''
shadowJar {
    exclude(dependency('shadow:b:1.0'))
}
'''
        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties', 'a2.properties'])

        and:
        doesNotContain(output, ['b.properties'])
    }

    private getOutput() {
        file('build/libs/shadow.jar')
    }
}
