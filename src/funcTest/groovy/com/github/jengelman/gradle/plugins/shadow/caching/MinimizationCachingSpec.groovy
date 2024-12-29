package com.github.jengelman.gradle.plugins.shadow.caching

class MinimizationCachingSpec extends AbstractCachingSpec {
    File output

    @Override
    String getShadowJarTask() {
        return ":server:shadowJar"
    }

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

            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            $projectBuildScript

            dependencies { implementation project(':client') }
        """.stripIndent()

        output = getFile('server/build/libs/server-all.jar')

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'junit/framework/Test.class',
            'client/Client.class'
        ])

        when:
        file('server/build.gradle') << """
            $shadowJar {
                minimize {
                    exclude(dependency('junit:junit:.*'))
                }
            }

            dependencies { implementation project(':client') }
        """.stripIndent()
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'junit/framework/Test.class'
        ])
        assertDoesNotContain(output, ['client/Client.class'])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'junit/framework/Test.class'
        ])
        assertDoesNotContain(output, ['client/Client.class'])
    }
}
