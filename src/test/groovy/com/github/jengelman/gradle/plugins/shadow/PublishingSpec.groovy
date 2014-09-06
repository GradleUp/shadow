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
}
