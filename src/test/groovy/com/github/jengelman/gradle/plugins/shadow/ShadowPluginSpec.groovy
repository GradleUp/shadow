package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.functional.ExecutionResult

class ShadowPluginSpec extends PluginSpecification {

    def 'apply plugin'() {
        given:
        String projectName = 'myshadow'
        String version = '1.0.0'

        Project project = ProjectBuilder.builder().withName(projectName).build()
        project.version = version

        when:
        project.plugins.apply(ShadowPlugin)

        then:
        project.plugins.hasPlugin(ShadowPlugin)

        and:
        ShadowJar shadow = project.tasks.findByName('shadowJar')
        assert shadow
        assert shadow.baseName == projectName
        assert shadow.destinationDir == new File(project.buildDir, 'libs')
        assert shadow.version == version
        assert shadow.classifier == 'all'
        assert shadow.extension == 'jar'

        and:
        Configuration shadowConfig = project.configurations.findByName('shadow')
        assert shadowConfig
        shadowConfig.artifacts.file.contains(shadow.archivePath)

    }

    def 'shadow copy'() {
        given:
        URL artifact = this.class.classLoader.getResource('test-artifact-1.0-SNAPSHOT.jar')
        URL project = this.class.classLoader.getResource('test-project-1.0-SNAPSHOT.jar')

        buildFile << """
            task shadow(type: ${ShadowJar.name}) {
                destinationDir = buildDir
                baseName = 'shadow'
                from('${artifact.path}')
                from('${project.path}')
            }
        """

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File output = file('build/shadow.jar')
        assert output.exists()
    }

    def 'include project resources'() {
        given:
        file('src/main/java/shadow/Passed.java') << '''package shadow;
public class Passed {}'''

        buildFile << """
apply plugin: ${ShadowPlugin.name}
repositories { jcenter() }
dependencies { compile 'junit:junit:3.8.2' }

shadowJar {
    baseName = 'shadow'
    classifier = null
}
"""
        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['shadow/Passed.class', 'junit/framework/Test.class'])
    }

    def 'include project dependencies'() {
        given:
        file('settings.gradle') << """
include 'client', 'server'
"""
        file('client/src/main/java/client/Client.java') << """package client;
public class Client {}
"""
        file('client/build.gradle') << """
apply plugin: 'java'
repositories { jcenter() }
dependencies { compile 'junit:junit:3.8.2' }
"""

        file('server/src/main/java/server/Server.java') << """package server;
import client.Client;
public class Server {}
"""
        file('server/build.gradle') << """
apply plugin: 'java'
apply plugin: ${ShadowPlugin.name}

repositories { jcenter() }
dependencies { compile project(':client') }

shadowJar {
    baseName = 'shadow'
    classifier = null
}
"""
        File serverOutput = file('server/build/libs/shadow.jar')

        when:
        runner.arguments << ':server:shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class',
                'junit/framework/Test.class'
        ])
    }

    def "exclude INDEX.LIST, *.SF, *.DSA, and *.RSA by default"() {
        given:
        AppendableMavenFileRepository repo = repo()

        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('META-INF/INDEX.LIST', 'JarIndex-Version: 1.0')
                .insertFile('META-INF/a.SF', 'Signature File')
                .insertFile('META-INF/a.DSA', 'DSA Signature Block')
                .insertFile('META-INF/a.RSA', 'RSA Signature Block')
                .insertFile('META-INF/a.properties', 'key=value')
                .publish()

        file('src/main/java/shadow/Passed.java') << '''package shadow;
public class Passed {}'''

        buildFile << """
apply plugin: ${ShadowPlugin.name}
repositories { maven { url "${repo.uri}" } }
dependencies { compile 'shadow:a:1.0' }

shadowJar {
    baseName = 'shadow'
    classifier = null
}
"""
        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties', 'META-INF/a.properties'])

        and:
        doesNotContain(output, ['META-INF/INDEX.LIST', 'META-INF/a.SF', 'META-INF/a.DSA', 'META-INF/a.RSA'])
    }

    def "include runtime configuration by default"() {
        given:
        AppendableMavenFileRepository repo = repo()

        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .publish()

        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        buildFile << """
apply plugin: ${ShadowPlugin.name}

repositories { maven { url "${repo.uri}" } }
dependencies {
    runtime 'shadow:a:1.0'
    shadow 'shadow:b:1.0'
}

shadowJar {
    baseName = 'shadow'
    classifier = null
}
"""
        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties'])

        and:
        doesNotContain(output, ['b.properties'])
    }

    private getOutput() {
        file('build/libs/shadow.jar')
    }
}
