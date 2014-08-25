package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.apache.tools.zip.ZipFile
import org.gradle.testkit.functional.ExecutionResult
import spock.lang.Issue

import java.util.jar.Attributes
import java.util.jar.JarFile

class PublishingSpec extends PluginSpecification {

    AppendableMavenFileRepository repo
    AppendableMavenFileRepository publishingRepo

    def setup() {
        repo = repo()
        publishingRepo = repo('remote_repo')
    }

    def "publish shadow jar with maven plugin"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()
        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        settingsFile << "rootProject.name = 'maven'"
        buildFile << """
            |apply plugin: ${ShadowPlugin.name}
            |apply plugin: 'maven'
            |apply plugin: 'java'
            |
            |group = 'shadow'
            |version = '1.0'
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies {
            |   compile 'shadow:a:1.0'
            |   shadow 'shadow:b:1.0'
            |}
            |
            |shadowJar {
            |   baseName = 'maven-all'
            |   classifier = null
            |}
            |
            |uploadShadow {
            |   repositories {
            |       mavenDeployer {
            |           repository(url: "${publishingRepo.uri}")
            |       }
            |   }
            |}
        """.stripMargin()

        when:
        runner.arguments << 'uploadShadow'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and: 'Check that shadow artifact exists'
        File publishedFile = publishingRepo.rootDir.file('shadow/maven-all/1.0/maven-all-1.0.jar').canonicalFile
        assert publishedFile.exists()

        and: 'Check contents of shadow artifact'
        contains(publishedFile, ['a.properties', 'a2.properties'])

        and: 'Check that shadow artifact pom exists and contents'
        File pom = publishingRepo.rootDir.file('shadow/maven-all/1.0/maven-all-1.0.pom').canonicalFile
        assert pom.exists()

        def contents = new XmlSlurper().parse(pom)
        assert contents.dependencies.size() == 1
        assert contents.dependencies[0].dependency.size() == 1

        def dependency = contents.dependencies[0].dependency[0]
        assert dependency.groupId.text() == 'shadow'
        assert dependency.artifactId.text() == 'b'
        assert dependency.version.text() == '1.0'
    }

    def "publish shadow jar with maven-publish plugin"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()
        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        settingsFile << "rootProject.name = 'maven'"
        buildFile << """
            |apply plugin: ${ShadowPlugin.name}
            |apply plugin: 'maven-publish'
            |apply plugin: 'java'
            |
            |group = 'shadow'
            |version = '1.0'
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies {
            |   compile 'shadow:a:1.0'
            |   shadow 'shadow:b:1.0'
            |}
            |
            |shadowJar {
            |   classifier = ''
            |   baseName = 'maven-all'
            |}
            |
            |publishing {
            |   publications {
            |       shadow(MavenPublication) {
            |           from components.shadow
            |           artifactId = 'maven-all'
            |       }
            |   }
            |   repositories {
            |       maven {
            |           url "${publishingRepo.uri}"
            |       }
            |   }
            |}
        """.stripMargin()

        when:
        runner.arguments << 'publish'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        File publishedFile = publishingRepo.rootDir.file('shadow/maven-all/1.0/maven-all-1.0.jar').canonicalFile
        assert publishedFile.exists()

        and:
        contains(publishedFile, ['a.properties', 'a2.properties'])

        and:
        File pom = publishingRepo.rootDir.file('shadow/maven-all/1.0/maven-all-1.0.pom').canonicalFile
        assert pom.exists()

        def contents = new XmlSlurper().parse(pom)
        assert contents.dependencies.size() == 1
        assert contents.dependencies[0].dependency.size() == 1

        def dependency = contents.dependencies[0].dependency[0]
        assert dependency.groupId.text() == 'shadow'
        assert dependency.artifactId.text() == 'b'
        assert dependency.version.text() == '1.0'
    }

    def 'integration with application plugin'() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()

        file('src/main/java/myapp/Main.java') << """
            |package myapp;
            |public class Main {
            |   public static void main(String[] args) {
            |       System.out.println("TestApp: Hello World! (" + args[0] + ")");
            |   }
            |}
        """.stripMargin()

        buildFile << """
            |apply plugin: ${ShadowPlugin.name}
            |apply plugin: 'application'
            |apply plugin: 'java'
            |
            |mainClassName = 'myapp.Main'
            |
            |version = '1.0'
            |
            |repositories {
            |   maven { url "${repo.uri}" }
            |}
            |
            |dependencies {
            |   compile 'shadow:a:1.0'
            |}
            |
            |runShadow {
            |   args 'foo'
            |}
        """.stripMargin()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        runner.arguments << 'runShadow'
        runner.arguments << 'installShadow'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and: 'tests that runShadow executed and exited'
        assert result.standardOutput.contains('TestApp: Hello World! (foo)')

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
            |package myapp;
            |public class Main {
            |   public static void main(String[] args) {
            |       System.out.println("TestApp: Hello World! (" + args[0] + ")");
            |   }
            |}
        """.stripMargin()

        buildFile << """
            |apply plugin: ${ShadowPlugin.name}
            |apply plugin: 'application'
            |apply plugin: 'java'
            |
            |mainClassName = 'myapp.Main'
            |
            |version = '1.0'
            |
            |repositories {
            |   maven { url "${repo.uri}" }
            |}
            |
            |dependencies {
            |   shadow 'shadow:a:1.0'
            |}
            |
            |runShadow {
            |   args 'foo'
            |}
        """.stripMargin()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        runner.arguments << 'distShadowZip'
        ExecutionResult result = runner.run()

        then:
        success(result)

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
}
