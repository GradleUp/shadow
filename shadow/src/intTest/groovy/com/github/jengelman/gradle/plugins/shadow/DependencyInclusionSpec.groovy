package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.support.AppendableJar
import com.github.jengelman.gradle.plugins.shadow.support.ShadowPluginIntegrationSpec

class DependencyInclusionSpec extends ShadowPluginIntegrationSpec {

    def "implicit inclusion of project artifact"() {
        given:
        file('src/main/java/shadow/Passed.java') << '''package shadow;

public class Passed {
}
'''
        buildFile << '''
repositories {
    jcenter()
}

dependencies {
    compile 'junit:junit:3.8.2'
}

shadow {
    artifactAttached = false
}
'''

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        contains(['shadow/Passed.class', 'junit/framework/Test.class'])
    }

    def "do not include non-runtime scope configuration in output"() {
        given:
        mavenRepo.module('shadow', 'compile', '1.0').insertFile('compile.properties', 'compile').publish()
        mavenRepo.module('shadow', 'provided', '1.0').insertFile('provided.properties', 'provided').publish()
        mavenRepo.module('shadow', 'runtime', '1.0').insertFile('runtime.properties', 'runtime').publish()
        mavenRepo.module('shadow', 'test', '1.0').insertFile('test.properties', 'test').publish()

        new AppendableJar(file('system.jar')).insertFile('system.properties', 'system').write()

        buildFile << """
configurations {
    system
    provided
}

repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    system files('system.jar')
    provided 'shadow:provided:1.0'
    compile 'shadow:compile:1.0'
    runtime 'shadow:runtime:1.0'
    testCompile 'shadow:test:1.0'
}

shadow {
    artifactAttached = false
}
"""
        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        contains(['compile.properties', 'runtime.properties'])
        doesNotContain(['system.propeties', 'provided.properties', 'test.properties'])
    }

    def "include file system dependencies"() {
        given:
        mavenRepo.module('shadow', 'compile', '1.0').insertFile('compile.properties', 'compile').publish()
        mavenRepo.module('shadow', 'runtime', '1.0').insertFile('runtime.properties', 'runtime')
                .dependsOn('shadow', 'compile', '1.0').publish()
        new AppendableJar(file('system.jar')).insertFile('system.properties', 'system').write()

        buildFile << """
repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    compile files('system.jar')
    runtime 'shadow:runtime:1.0'
}

shadow {
    artifactAttached = false
}
"""

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        contains(['compile.properties', 'runtime.properties', 'system.properties'])
    }
}
