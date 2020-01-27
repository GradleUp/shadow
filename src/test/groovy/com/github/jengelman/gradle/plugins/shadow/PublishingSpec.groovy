package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import spock.lang.Issue

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
            apply plugin: 'maven'

            dependencies {
               compile 'shadow:a:1.0'
               shadow 'shadow:b:1.0'
            }
            
            shadowJar {
               baseName = 'maven-all'
               classifier = null
            }
            
            uploadShadow {
               repositories {
                   mavenDeployer {
                       repository(url: "${publishingRepo.uri}")
                   }
               }
            }
        """.stripIndent()

        when:
        runner.withArguments('uploadShadow').build()

        then: 'Check that shadow artifact exists'
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

    @Issue('SHADOW-347')
    def "maven install with application plugin"() {
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
            apply plugin: 'maven'
            apply plugin: 'application'

            mainClassName = 'my.App'

            dependencies {
               compile 'shadow:a:1.0'
               shadow 'shadow:b:1.0'
            }
        """.stripIndent()

        when:
        runner.withArguments('install').withDebug(true).build()

        then:
        noExceptionThrown()
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
            apply plugin: 'maven-publish'

            dependencies {
               compile 'shadow:a:1.0'
               shadow 'shadow:b:1.0'
            }
            
            shadowJar {
               classifier = ''
               baseName = 'maven-all'
            }
            
            publishing {
               publications {
                   shadow(MavenPublication) { publication ->
                       project.shadow.component(publication)
                       artifactId = 'maven-all'
                   }
               }
               repositories {
                   maven {
                       url "${publishingRepo.uri}"
                   }
               }
            }
        """.stripIndent()

        when:
        runner.withArguments('publish').build()

        then:
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

    def "publish multiproject shadow jar with maven-publish plugin"() {
        given:

        settingsFile << """
            rootProject.name = 'maven'
            include 'a'
            include 'b'
            include 'c'
        """.stripMargin()

        buildFile.text = """
            subprojects {
                apply plugin: 'java'
                apply plugin: 'maven-publish'

                version = "1.0"
                group = 'shadow'
    
                repositories { maven { url "${repo.uri}" } }
                publishing {
                   repositories {
                       maven {
                           url "${publishingRepo.uri}"
                       }
                   }
                }
            }
        """.stripIndent()

        file('a/build.gradle') << """
            plugins {
                id 'java'
                id 'maven-publish'
            }
        """.stripMargin()

        file('a/src/main/resources/a.properties') << 'a'
        file('a/src/main/resources/a2.properties') << 'a2'

        file('b/build.gradle') << """
            plugins {
                id 'java'
                id 'maven-publish'
            }
        """.stripMargin()

        file('b/src/main/resources/b.properties') << 'b'

        file('c/build.gradle') << """
            plugins {
                id 'com.github.johnrengelman.shadow'
            }
            
            dependencies {
                compile project(':a')
                shadow project(':b')
            }

            shadowJar {
               classifier = ''
               baseName = 'maven-all'
            }
            
            publishing {
               publications {
                   shadow(MavenPublication) { publication ->
                       project.shadow.component(publication)
                       artifactId = 'maven-all'
                   }
               }
            }
        """.stripMargin()

        when:
        runner.withArguments('publish').build()

        then:
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
