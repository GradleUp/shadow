package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.integration.PluginIntegrationSpec

class AppendingTransformerSpec extends PluginIntegrationSpec {

    def setup() {
        applyPlugin('shadow')
    }

    def "append resources"() {
        given:
        buildFile << """
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer

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
    }
}
