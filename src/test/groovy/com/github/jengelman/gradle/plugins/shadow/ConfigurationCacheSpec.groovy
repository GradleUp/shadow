package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
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
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()

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
}
