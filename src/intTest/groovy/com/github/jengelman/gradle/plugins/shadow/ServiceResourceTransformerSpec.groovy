package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.support.ShadowPluginIntegrationSpec
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

class ServiceResourceTransformerSpec extends ShadowPluginIntegrationSpec {

    def "combines service resources"() {
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
import ${ServiceFileTransformer.name}

repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    compile 'shadow:one:0.1'
    compile 'shadow:two:0.1'
}

shadow {
    artifactAttached = false
    transformer(ServiceFileTransformer)
}
"""

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        assert shadowOutput.exists()

        and:
        String text = getJarFileContents(shadowOutput, 'META-INF/services/org.apache.maven.Shade')
        assert text.split('(\r\n)|(\r)|(\n)').size() == 2
    }
}
