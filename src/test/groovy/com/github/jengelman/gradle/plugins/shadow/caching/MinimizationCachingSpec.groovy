package com.github.jengelman.gradle.plugins.shadow.caching

import org.gradle.testkit.runner.BuildResult

import static org.gradle.testkit.runner.TaskOutcome.*

class MinimizationCachingSpec extends AbstractCachingSpec {
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
            dependencies { compile 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            repositories { maven { url "${repo.uri}" } }
            dependencies { compile project(':client') }
        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server-all.jar')

        when:
        BuildResult result = runWithCacheEnabled(':server:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'server/Server.class',
                'junit/framework/Test.class',
                'client/Client.class'
        ])

        and:
        result.task(':server:shadowJar').outcome == SUCCESS

        when:
        serverOutput.delete()
        file('server/build.gradle').text = """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                minimize {
                    exclude(dependency('junit:junit:.*'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { compile project(':client') }
        """.stripIndent()
        runWithCacheEnabled(':server:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'server/Server.class',
                'junit/framework/Test.class'
        ])
        doesNotContain(serverOutput, ['client/Client.class'])

        and:
        result.task(':server:shadowJar').outcome == SUCCESS

        when:
        serverOutput.delete()
        result = runWithCacheEnabled(':server:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'server/Server.class',
                'junit/framework/Test.class'
        ])
        doesNotContain(serverOutput, ['client/Client.class'])

        and:
        result.task(':server:shadowJar').outcome == FROM_CACHE
    }
}
