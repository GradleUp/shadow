package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.integration.TestFile
import com.github.jengelman.gradle.plugins.shadow.support.ShadowPluginIntegrationSpec
import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestResourceTransformer

import java.util.jar.JarInputStream
import java.util.jar.Manifest

class ManifestSpec extends ShadowPluginIntegrationSpec {

    def "manifest retained"() {
        given:
        TestFile main = file('src/main/java/shadow/Main.java')
        main << '''package shadow;

public class Main {

    public static void main(String[] args) {
    }
}
'''
        buildFile << '''
jar {
    manifest {
        attributes 'Main-Class': 'shadow.Main'
        attributes 'Test-Entry': 'PASSED'
    }
}

shadow {
    classifier = 'shadow'
}
'''

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        def shadow = shadowOutput('shadow')
        assert shadow.exists()

        and:
        JarInputStream jis = new JarInputStream(shadow.newInputStream())
        Manifest mf = jis.manifest
        jis.close()

        assert mf
        assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Main-Class') == 'shadow.Main'

    }

    def "manifest transformed"() {
        given:
        TestFile main = file('src/main/java/shadow/Main.java')
        main << '''package shadow;

class Main {

    public static void main(String[] args) {
    }
}
'''
        buildFile << """
import ${ManifestResourceTransformer.name}

jar {
    manifest {
        attributes 'Test-Entry': 'FAILED'
        attributes 'Original-Entry': 'PASSED'
    }
}

shadow {
    artifactAttached = false
    transformer(ManifestResourceTransformer) {
        attributes 'Main-Class': 'shadow.Main'
        attributes 'Test-Entry': 'PASSED'
    }
}
"""

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        shadowOutput.exists()

        and:
        JarInputStream jis = new JarInputStream(shadowOutput.newInputStream())
        Manifest mf = jis.manifest
        jis.close()

        assert mf
        assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Original-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Main-Class') == 'shadow.Main'

    }
}
