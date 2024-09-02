package com.github.jengelman.gradle.plugins.shadow.caching

import org.gradle.testkit.runner.BuildResult

import static org.gradle.testkit.runner.TaskOutcome.*

class MinimizationCachingSpec extends AbstractCachingSpec {
    File output
    String shadowJarTask = ":server:shadowJar"

    /**
     * Ensure that we get a cache miss when minimization is added and that caching works with minimization
     */
    def 'shadowJar is cached correctly when minimization is added'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.gradleup.shadow'

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        output = getFile('server/build/libs/server-all.jar')

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        contains(output, [
                'server/Server.class',
                'junit/framework/Test.class',
                'client/Client.class'
        ])

        when:
        file('server/build.gradle').text = """
            apply plugin: 'java'
            apply plugin: 'com.gradleup.shadow'

            shadowJar {
                minimize {
                    exclude(dependency('junit:junit:.*'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()
        assertShadowJarExecutes()

        then:
        output.exists()
        contains(output, [
                'server/Server.class',
                'junit/framework/Test.class'
        ])
        doesNotContain(output, ['client/Client.class'])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        contains(output, [
                'server/Server.class',
                'junit/framework/Test.class'
        ])
        doesNotContain(output, ['client/Client.class'])
    }
}
