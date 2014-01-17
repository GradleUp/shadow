package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.support.ShadowPluginIntegrationSpec

class AppendingTransformerSpec extends ShadowPluginIntegrationSpec {

    def "append resources"() {
        given:
        mavenRepo.module('shadow', 'one', '0.1')
                .insertFile('META-INF/services/org.apache.maven.Shade',
                    'one # NOTE: No newline terminates this line/file')
                .publish()
        mavenRepo.module('shadow', 'two', '0.1')
                .insertFile('META-INF/services/org.apache.maven.Shade',
                    'two # NOTE: No newline terminates this line/file')
                .publish()

        buildFile << """
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer

repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    compile "shadow:one:0.1"
    compile "shadow:two:0.1"
}

shadow {
    artifactAttached = false
    transformer(AppendingTransformer) {
        resource = 'META-INF/services/org.apache.maven.Shade'
    }
}
"""
        when:
        execute('shadowJar')

        then:
        buildSuccessful()
        assert results.successful

        and:
        assert shadowOutput.exists()

        and:
        assertJarFileContentsEqual(shadowOutput, 'META-INF/services/org.apache.maven.Shade',
'''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file
'''
        )
    }
}
