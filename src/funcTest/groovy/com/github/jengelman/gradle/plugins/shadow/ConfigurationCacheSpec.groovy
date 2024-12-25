package com.github.jengelman.gradle.plugins.shadow

class ConfigurationCacheSpec extends BasePluginSpecification {

    @Override
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
               implementation 'shadow:a:1.0'
               implementation 'shadow:b:1.0'
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

            application {
               mainClass = 'myapp.Main'
            }

            dependencies {
               implementation 'shadow:a:1.0'
            }

            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        run('shadowJar')
        def result = run('shadowJar')

        then:
        result.output.contains("Reusing configuration cache.")
    }

    def "configuration caching supports includes"() {
        given:
        buildFile << """
            $shadowJar {
               exclude 'a2.properties'
            }
        """.stripIndent()

        when:
        run('shadowJar')
        output.delete()
        def result = run('shadowJar')

        then:
        contains(output, ['a.properties', 'b.properties'])

        and:
        doesNotContain(output, ['a2.properties'])
        result.output.contains("Reusing configuration cache.")
    }

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

            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        and:
        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()
        file('server/build.gradle') << """
            $defaultBuildScript

            $shadowJar {
                minimize {
                    exclude(dependency('junit:junit:.*'))
                }
            }

            dependencies { implementation project(':client') }
        """.stripIndent()

        and:
        def output = getFile('server/build/libs/server-all.jar')

        when:
        run('shadowJar', '-s')
        output.delete()
        def result = run('shadowJar', '-s')

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

    def "configuration caching of configurations is up-to-date"() {
        given:
        file('settings.gradle') << """
            include 'lib'
        """.stripIndent()

        and:
        file('lib/src/main/java/lib/Lib.java') << """
            package lib;
            public class Lib {}
        """.stripIndent()
        file('lib/build.gradle') << """
            $defaultBuildScript

            dependencies {
                implementation "junit:junit:3.8.2"
            }

            $shadowJar {
                configurations = [project.configurations.compileClasspath]
            }

        """.stripIndent()

        when:
        run('shadowJar', '-s')
        def result = run('shadowJar', '-s')

        then:
        result.output.contains(":lib:shadowJar UP-TO-DATE")
    }
}
