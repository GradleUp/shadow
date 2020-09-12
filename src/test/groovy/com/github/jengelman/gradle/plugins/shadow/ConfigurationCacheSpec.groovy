package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import groovy.transform.NotYetImplemented
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf

@IgnoreIf({ GradleVersion.current().baseVersion < GradleVersion.version("6.6") })
class ConfigurationCacheSpec extends PluginSpecification {

    def setup() {
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()
        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        buildFile << """
            dependencies {
               compile 'shadow:a:1.0'
               compile 'shadow:b:1.0'
            }
        """.stripIndent()
    }

    def "supports configuration cache"() {
        given:
        file('src/main/java/myapp/Main.java') << """
            package myapp;
            public class Main {
               public static void main(String[] args) {
                   System.out.println("TestApp: Hello World! (" + args[0] + ")");
               }
            }
        """.stripIndent()

        buildFile << """
            apply plugin: 'application'

            mainClassName = 'myapp.Main'
            
            dependencies {
               compile 'shadow:a:1.0'
            }
            
            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        runner.withArguments('--configuration-cache', 'shadowJar').build()
        def result = runner.withArguments('--configuration-cache', 'shadowJar').build()

        then:
        result.output.contains("Reusing configuration cache.")
    }

    def "configuration caching supports includes"() {
        given:
        buildFile << """
            shadowJar {
               exclude 'a2.properties'
            }
        """.stripIndent()

        when:
        runner.withArguments('--configuration-cache', 'shadowJar').build()
        output.delete()
        def result = runner.withArguments('--configuration-cache', 'shadowJar').build()

        then:
        contains(output, ['a.properties', 'b.properties'])

        and:
        doesNotContain(output, ['a2.properties'])
        result.output.contains("Reusing configuration cache.")
    }

    @NotYetImplemented
    def "configuration caching supports minimize"() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        and:
        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()
        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { compile 'junit:junit:3.8.2' }
        """.stripIndent()

        and:
        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()
        file('server/build.gradle') << """
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

        and:
        def output = getFile('server/build/libs/server-all.jar')

        when:
        runner.withArguments('--configuration-cache', 'shadowJar', '-s').build()
        output.delete()
        def result = runner.withArguments('--configuration-cache', 'shadowJar', '-s').build()

        then:
        output.exists()
        contains(output, [
                'server/Server.class',
                'junit/framework/Test.class'
        ])
        doesNotContain(output, ['client/Client.class'])

        and:
        result.output.contains("Reusing configuration cache.")
    }
}
