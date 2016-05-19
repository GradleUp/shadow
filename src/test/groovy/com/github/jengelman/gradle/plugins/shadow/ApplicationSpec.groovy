package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.apache.tools.zip.ZipFile
import org.gradle.testkit.runner.BuildResult
import spock.lang.Issue

import java.util.jar.Attributes
import java.util.jar.JarFile

class ApplicationSpec extends PluginSpecification {

    AppendableMavenFileRepository repo
    AppendableMavenFileRepository publishingRepo

    def setup() {
        repo = repo()
        publishingRepo = repo('remote_repo')
    }

    def 'integration with application plugin'() {
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
            apply plugin: 'com.github.johnrengelman.shadow'
            apply plugin: 'application'
            apply plugin: 'java'
            
            mainClassName = 'myapp.Main'
            
            version = '1.0'
            
            repositories {
               maven { url "${repo.uri}" }
            }
            
            dependencies {
               compile 'shadow:a:1.0'
            }
            
            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        BuildResult result = runner.withArguments('runShadow').build()

        then: 'tests that runShadow executed and exited'
        assert result.output.contains('TestApp: Hello World! (foo)')

        and: 'Check that the proper jar file was installed'
        File installedJar = file('build/installShadow/myapp/lib/myapp-1.0-all.jar')
        assert installedJar.exists()

        and: 'And that jar file as the correct files in it'
        contains(installedJar, ['a.properties', 'a2.properties', 'myapp/Main.class'])

        and: 'Check the manifest attributes in the jar file are correct'
        JarFile jar = new JarFile(installedJar)
        Attributes attributes = jar.manifest.mainAttributes
        assert attributes.getValue('Main-Class') == 'myapp.Main'

        then: 'Check that the start scripts is written out and has the correct Java invocation'
        File startScript = file('build/installShadow/myapp/bin/myapp')
        assert startScript.exists()
        assert startScript.text.contains("-jar \$APP_HOME/lib/myapp-1.0-all.jar")

        cleanup:
        jar?.close()
    }

    @Issue('SHADOW-89')
    def 'shadow application distributions should use shadow jar'() {
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
            apply plugin: 'com.github.johnrengelman.shadow'
            apply plugin: 'application'
            apply plugin: 'java'
            
            mainClassName = 'myapp.Main'
            
            version = '1.0'
            
            repositories {
               maven { url "${repo.uri}" }
            }
            
            dependencies {
               shadow 'shadow:a:1.0'
            }
            
            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        runner.withArguments('distShadowZip').build()

        then: 'Check that the distribution zip was created'
        File zip = file('build/distributions/myapp-1.0.zip')
        assert zip.exists()

        and: 'Check that the zip contains the correct library files & scripts'
        ZipFile zipFile = new ZipFile(zip)
        assert zipFile.entries.find { it.name == 'myapp-1.0/lib/myapp-1.0-all.jar' }
        assert zipFile.entries.find { it.name == 'myapp-1.0/lib/a-1.0.jar'}

        cleanup:
        zipFile?.close()
    }

    @Issue('SHADOW-90')
    def 'installShadow does not execute dependent shadow task'() {
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
            apply plugin: 'com.github.johnrengelman.shadow'
            apply plugin: 'application'
            apply plugin: 'java'
            
            mainClassName = 'myapp.Main'
            
            version = '1.0'
            
            repositories {
               maven { url "${repo.uri}" }
            }
            
            dependencies {
               compile 'shadow:a:1.0'
            }
            
            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        runner.withArguments('installShadow').build()

        then: 'Check that the proper jar file was installed'
        File installedJar = file('build/installShadow/myapp/lib/myapp-1.0-all.jar')
        assert installedJar.exists()
    }
}
