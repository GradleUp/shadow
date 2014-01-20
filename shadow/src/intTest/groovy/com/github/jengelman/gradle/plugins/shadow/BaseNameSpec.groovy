package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.integration.TestFile
import com.github.jengelman.gradle.plugins.shadow.support.ShadowPluginIntegrationSpec

class BaseNameSpec extends ShadowPluginIntegrationSpec {

    def "same base name"() {
        given:
        TestFile main = testDirectory.createFile('src/main/java/samebasename/Main.java')
        main.text = '''package samebasename;

class Main {
    static void main(String[] args) { }
}'''

        buildFile << """
jar {
    baseName 'artifact'
}

shadow {
    baseName 'artifact'
    artifactAttached false
}
"""

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        assert file('build/distributions/artifact-0.1.jar').exists()

        and:
        assert file('build/libs/artifact-0.1.jar').exists()
    }
}
