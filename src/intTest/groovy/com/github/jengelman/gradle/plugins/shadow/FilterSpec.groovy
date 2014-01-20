package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.support.ShadowPluginIntegrationSpec

class FilterSpec extends ShadowPluginIntegrationSpec {

    def 'filter artifact contents'() {
        given:
        mavenRepo.module('shadow', 'a', '0.1')
                .insertFile('a.properties', 'a.properties')
                .insertFile('org/a.properties', 'org/a.properties')
                .insertFile('org/apache/a.properties', 'org/apache/a.properties')
                .insertFile('org/apache/maven/a.properties', 'org/apaches/maven/a.properties')
                .insertFile('META-INF/maven/shadow/a/pom.properties', 'pom.properties')
                .publish()
        mavenRepo.module('shadow', 'b', '0.1').artifact([classifier: 'client'])
                .insertFile('client', 'b.properties', 'b.properties')
                .insertFile('client', 'org/b.properties', 'org/b.properties')
                .insertFile('client', 'org/apache/b.properties', 'org/apache/b.properties')
                .insertFile('client', 'org/apache/maven/b.properties', 'org/apache/maven/b.properties')
                .insertFile('client', 'org/apache/maven/b/b.properties', 'org/apache/maven/b/b.properties')
                .insertFile('client', 'META-INF/maven/shadow/b/pom.properties', 'pom.properties')
                .publish()

        buildFile << """
repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    compile 'shadow:a:0.1'
    compile 'shadow:b:0.1:client'
}

shadow {
    artifactAttached = false
    filter('shadow:a') {
        include '**/a.properties'
    }
    filter('shadow:b:client') {
        exclude 'org/apache/*'
        exclude 'org/apache/maven/b/'
    }
    filter('*:*') {
        exclude 'org/*'
    }
}
"""

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        contains([
                'a.properties',
                'org/apache/a.properties',
                'org/apache/maven/a.properties',
                'b.properties',
                'org/apache/maven/b.properties'
        ])
        doesNotContain([
                'META-INF/maven/shadow/a/pom.properties',
                'org/a.properties',
                'org/b.properties',
                'org/apache/b.properties',
                'org/apache/maven/b/b.properties'
        ])
    }

    def 'filter artifact contents with shortcuts'() {
        given:
        mavenRepo.module('shadow', 'a', '0.1')
                .insertFile('a.properties', 'a.properties')
                .insertFile('org/a.properties', 'org/a.properties')
                .insertFile('org/apache/a.properties', 'org/apache/a.properties')
                .insertFile('org/apache/maven/a.properties', 'org/apaches/maven/a.properties')
                .insertFile('META-INF/maven/shadow/a/pom.properties', 'pom.properties')
                .publish()
        mavenRepo.module('shadow', 'b', '0.1').artifact([classifier: 'client'])
                .insertFile('client', 'b.properties', 'b.properties')
                .insertFile('client', 'org/b.properties', 'org/b.properties')
                .insertFile('client', 'org/apache/b.properties', 'org/apache/b.properties')
                .insertFile('client', 'org/apache/maven/b.properties', 'org/apache/maven/b.properties')
                .insertFile('client', 'org/apache/maven/b/b.properties', 'org/apache/maven/b/b.properties')
                .insertFile('client', 'META-INF/maven/shadow/b/pom.properties', 'pom.properties')
                .publish()

        buildFile << """
repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    compile 'shadow:a:0.1'
    compile 'shadow:b:0.1:client'
}

shadow {
    artifactAttached = false
    filter('shadow:a') {
        include '**/a.properties'
    }
    filter('shadow:b:client') {
        exclude 'org/apache/*'
        exclude 'org/apache/maven/b/'
    }
    exclude 'org/*'
    exclude 'META-INF/**/'
}
"""

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        contains([
                'a.properties',
                'org/apache/a.properties',
                'org/apache/maven/a.properties',
                'b.properties',
                'org/apache/maven/b.properties'
        ])
        doesNotContain([
                'META-INF/maven/shadow/a/pom.properties',
                'org/a.properties',
                'org/b.properties',
                'org/apache/b.properties',
                'org/apache/maven/b/b.properties',
                'META-INF/maven/shadow/b/pom.properties',
        ])
    }
}
