package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Ignore
import spock.lang.Issue

import java.util.jar.Attributes
import java.util.jar.JarFile

class ShadowPluginSpec extends BasePluginSpecification {

    def 'shadow a project shadow jar'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            $defaultProjectBuildScript

            dependencies { implementation 'junit:junit:3.8.2' }

            $shadowJar {
               relocate 'junit.framework', 'client.junit.framework'
            }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;

            import client.Client;
            import client.junit.framework.Test;

            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            $defaultProjectBuildScript

            dependencies { implementation project(path: ':client', configuration: 'shadow') }
        """.stripIndent()

        File serverOutput = file('server/build/libs/server-all.jar')

        when:
        run(':server:shadowJar')

        then:
        serverOutput.exists()
        assertContains(serverOutput, [
            'client/Client.class',
            'client/junit/framework/Test.class',
            'server/Server.class',
        ])

        and:
        assertDoesNotContain(serverOutput, [
            'junit/framework/Test.class'
        ])
    }

    def "exclude INDEX.LIST, *.SF, *.DSA, and *.RSA by default"() {
        given:
        repo.module('shadow', 'a', '1.0')
            .insertFile('a.properties', 'a')
            .insertFile('META-INF/INDEX.LIST', 'JarIndex-Version: 1.0')
            .insertFile('META-INF/a.SF', 'Signature File')
            .insertFile('META-INF/a.DSA', 'DSA Signature Block')
            .insertFile('META-INF/a.RSA', 'RSA Signature Block')
            .insertFile('META-INF/a.properties', 'key=value')
            .publish()

        file('src/main/java/shadow/Passed.java') << '''
            package shadow;
            public class Passed {}
        '''.stripIndent()

        projectScriptFile << """
            dependencies { implementation 'shadow:a:1.0' }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assertContains(outputShadowJar, ['a.properties', 'META-INF/a.properties'])

        and:
        assertDoesNotContain(outputShadowJar, ['META-INF/INDEX.LIST', 'META-INF/a.SF', 'META-INF/a.DSA', 'META-INF/a.RSA'])
    }

    def "include runtime configuration by default"() {
        given:
        repo.module('shadow', 'a', '1.0')
            .insertFile('a.properties', 'a')
            .publish()

        repo.module('shadow', 'b', '1.0')
            .insertFile('b.properties', 'b')
            .publish()

        projectScriptFile << """
            dependencies {
               runtimeOnly 'shadow:a:1.0'
               shadow 'shadow:b:1.0'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assertContains(outputShadowJar, ['a.properties'])

        and:
        assertDoesNotContain(outputShadowJar, ['b.properties'])
    }

    def "include java-library configurations by default"() {
        given:
        repo.module('shadow', 'api', '1.0')
            .insertFile('api.properties', 'api')
            .publish()

        repo.module('shadow', 'implementation-dep', '1.0')
            .insertFile('implementation-dep.properties', 'implementation-dep')
            .publish()

        repo.module('shadow', 'implementation', '1.0')
            .insertFile('implementation.properties', 'implementation')
            .dependsOn('implementation-dep')
            .publish()

        repo.module('shadow', 'runtimeOnly', '1.0')
            .insertFile('runtimeOnly.properties', 'runtimeOnly')
            .publish()

        projectScriptFile.text = getDefaultProjectBuildScript('java-library', true, true)
        projectScriptFile << """
            dependencies {
               api 'shadow:api:1.0'
               implementation 'shadow:implementation:1.0'
               runtimeOnly 'shadow:runtimeOnly:1.0'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assertContains(outputShadowJar, ['api.properties', 'implementation.properties',
                                         'runtimeOnly.properties', 'implementation-dep.properties'])
    }

    def "doesn't include compileOnly configuration by default"() {
        given:
        repo.module('shadow', 'a', '1.0')
            .insertFile('a.properties', 'a')
            .publish()

        repo.module('shadow', 'b', '1.0')
            .insertFile('b.properties', 'b')
            .publish()

        projectScriptFile << """
            dependencies {
               runtimeOnly 'shadow:a:1.0'
               compileOnly 'shadow:b:1.0'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assertContains(outputShadowJar, ['a.properties'])

        and:
        assertDoesNotContain(outputShadowJar, ['b.properties'])
    }

    def "default copying strategy"() {
        given:
        repo.module('shadow', 'a', '1.0')
            .insertFile('META-INF/MANIFEST.MF', 'MANIFEST A')
            .publish()

        repo.module('shadow', 'b', '1.0')
            .insertFile('META-INF/MANIFEST.MF', 'MANIFEST B')
            .publish()

        projectScriptFile << """
            dependencies {
               runtimeOnly 'shadow:a:1.0'
               runtimeOnly 'shadow:b:1.0'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        JarFile jar = new JarFile(outputShadowJar)
        assert jar.entries().collect().size() == 2
    }

    def "Class-Path in Manifest not added if empty"() {
        given:

        projectScriptFile << """
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert outputShadowJar.exists()

        and:
        JarFile jar = new JarFile(outputShadowJar)
        Attributes attributes = jar.manifest.getMainAttributes()
        assert attributes.getValue('Class-Path') == null
    }

    @Issue('https://github.com/GradleUp/shadow/issues/65')
    def "add shadow configuration to Class-Path in Manifest"() {
        given:

        projectScriptFile << """
            dependencies {
              shadow 'junit:junit:3.8.2'
            }

            jar {
               manifest {
                   attributes 'Class-Path': '/libs/a.jar'
               }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert outputShadowJar.exists()

        and: 'https://github.com/GradleUp/shadow/issues/65 - combine w/ existing Class-Path'
        JarFile jar = new JarFile(outputShadowJar)
        Attributes attributes = jar.manifest.getMainAttributes()
        String classpath = attributes.getValue('Class-Path')
        assert classpath == '/libs/a.jar junit-3.8.2.jar'

    }

    @Issue('https://github.com/GradleUp/shadow/issues/92')
    def "do not include null value in Class-Path when jar file does not contain Class-Path"() {
        given:

        projectScriptFile << """
            dependencies { shadow 'junit:junit:3.8.2' }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert outputShadowJar.exists()

        and:
        JarFile jar = new JarFile(outputShadowJar)
        Attributes attributes = jar.manifest.getMainAttributes()
        String classpath = attributes.getValue('Class-Path')
        assert classpath == 'junit-3.8.2.jar'

    }

    @Issue('https://github.com/GradleUp/shadow/issues/203')
    def "support ZipCompression.STORED"() {
        given:

        projectScriptFile << """
            dependencies { shadow 'junit:junit:3.8.2' }

            $shadowJar {
                zip64 = true
                entryCompression = org.gradle.api.tasks.bundling.ZipEntryCompression.STORED
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert outputShadowJar.exists()

    }

    def 'api project dependency with version'() {
        given:
        file('settings.gradle') << """
            include 'api', 'lib', 'impl'
        """.stripIndent()

        file('lib/build.gradle') << """
            apply plugin: 'java'
            version = '1.0'

        """.stripIndent()

        file('api/src/main/java/api/UnusedEntity.java') << """
            package api;
            public class UnusedEntity {}
        """.stripIndent()

        file('api/build.gradle') << """
            apply plugin: 'java'
            version = '1.0'

            dependencies {
                implementation 'junit:junit:3.8.2'
                implementation project(':lib')
            }
        """.stripIndent()

        file('impl/build.gradle') << """
            ${getDefaultProjectBuildScript('java-library')}

            version = '1.0'

            dependencies { api project(':api') }

            shadowJar.minimize()
        """.stripIndent()

        File serverOutput = file('impl/build/libs/impl-1.0-all.jar')

        when:
        run(':impl:shadowJar')

        then:
        serverOutput.exists()
        assertContains(serverOutput, [
            'api/UnusedEntity.class',
        ])
    }

    @Issue('https://github.com/GradleUp/shadow/issues/143')
    @Ignore("This spec requires > 15 minutes and > 8GB of disk space to run")
    def "check large zip files with zip64 enabled"() {
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

        projectScriptFile << """
            apply plugin: 'application'

            application {
               mainClass = 'myapp.Main'
            }

            dependencies {
               implementation 'shadow:a:1.0'
            }

            def generatedResourcesDir = new File(project.layout.buildDirectory.asFile.get(), "generated-resources")

            task generateResources {
                doLast {
                    def rnd = new Random()
                    def buf = new byte[128 * 1024]
                    for (x in 0..255) {
                        def dir = new File(generatedResourcesDir, x.toString())
                        dir.mkdirs()
                        for (y in 0..255) {
                            def file = new File(dir, y.toString())
                            rnd.nextBytes(buf)
                            file.bytes = buf
                        }
                    }
                }
            }

            sourceSets {
                main {
                    output.dir(generatedResourcesDir, builtBy: generateResources)
                }
            }

            $shadowJar {
                zip64 = true
            }

            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsScriptFile << "rootProject.name = 'myapp'"

        when:
        BuildResult result = run('runShadow')

        then: 'tests that runShadow executed and exited'
        assert result.output.contains('TestApp: Hello World! (foo)')


    }

    @Issue("https://github.com/GradleUp/shadow/issues/609")
    def "doesn't error when using application mainClass property"() {
        given:
        projectScriptFile.text = getDefaultProjectBuildScript()

        projectScriptFile << """
            project.ext {
                aspectjVersion = '1.8.12'
            }

            apply plugin: 'application'

            application {
                mainClass.set('myapp.Main')
            }

            runShadow {
               args 'foo'
            }

        """

        file('src/main/java/myapp/Main.java') << """
            package myapp;
            public class Main {
               public static void main(String[] args) {
                   System.out.println("TestApp: Hello World! (" + args[0] + ")");
               }
            }
        """.stripIndent()

        when:
        BuildResult result = run('runShadow')

        then: 'tests that runShadow executed and exited'
        assert result.output.contains('TestApp: Hello World! (foo)')
    }

    @Issue("https://github.com/GradleUp/shadow/pull/459")
    def 'exclude gradleApi() by default'() {
        given:
        projectScriptFile.text = getDefaultProjectBuildScript('java-gradle-plugin', true, true)

        file('src/main/java/my/plugin/MyPlugin.java') << """
            package my.plugin;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            public class MyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    System.out.println("MyPlugin: Hello World!");
                }
            }
        """.stripIndent()
        file('src/main/resources/META-INF/gradle-plugins/my.plugin.properties') << """
            implementation-class=my.plugin.MyPlugin
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert outputShadowJar.exists()

        and:
        JarFile jar = new JarFile(outputShadowJar)
        assert jar.entries().collect().findAll { it.name.endsWith('.class') }.size() == 1
    }

    @Issue("https://github.com/GradleUp/shadow/issues/1070")
    def 'can register a custom shadow jar task'() {
        projectScriptFile << """
            dependencies {
              testImplementation 'junit:junit:3.8.2'
            }

            def testShadowJar = tasks.register('testShadowJar', ${ShadowJar.name}) {
              group = com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.GROUP_NAME
              description = "Create a combined JAR of project and test dependencies"

              archiveClassifier = "tests"
              from sourceSets.test.output
              configurations = [project.configurations.testRuntimeClasspath]
            }
        """.stripIndent()

        when:
        def result = run('testShadowJar')

        then:
        assert result.task(":testShadowJar").outcome == TaskOutcome.SUCCESS

        and:
        def jarFile = new JarFile(file("build/libs/shadow-1.0-tests.jar"))
        assert jarFile.getEntry('junit') != null
    }
}
