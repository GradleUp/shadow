package com.github.jengelman.gradle.plugins.shadow.caching

class MinimizationCachingSpec extends AbstractCachingSpec {

    @Override
    String getShadowJarTask() {
        return ":server:shadowJar"
    }

    @Override
    File getOutputShadowJar() {
        return file('server/build/libs/server-all.jar')
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
            $defaultProjectBuildScript

            dependencies { implementation project(':client') }
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        outputShadowJar.exists()
        containsEntries(outputShadowJar, [
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
        outputShadowJar.exists()
        containsEntries(outputShadowJar, [
            'server/Server.class',
            'junit/framework/Test.class'
        ])
        doesNotContainEntries(outputShadowJar, ['client/Client.class'])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        outputShadowJar.exists()
        containsEntries(outputShadowJar, [
            'server/Server.class',
            'junit/framework/Test.class'
        ])
        doesNotContainEntries(outputShadowJar, ['client/Client.class'])
    }
}
