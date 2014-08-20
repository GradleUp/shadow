package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.functional.ExecutionResult
import org.gradle.testkit.functional.GradleRunner
import org.gradle.testkit.functional.GradleRunnerFactory
import spock.lang.Issue
import spock.lang.Unroll

import java.util.jar.Attributes
import java.util.jar.JarFile

class ShadowPluginSpec extends PluginSpecification {

    def setup() {
        repo.module('junit', 'junit', '4.11').use(getTestJar("junit-4.11.jar")).publish()
        repo.module('org.hamcrest', 'hamcrest-core', '1.3').use(getTestJar("hamcrest-core-1.3.jar")).publish()
    }

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
        assert !project.tasks.findByName('shadowJar')

        when:
        project.plugins.apply(JavaPlugin)

        then:
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

    @Unroll
    def 'apply plugin and run in Gradle #version'() {
        given:
        GradleRunner versionRunner = GradleRunnerFactory.create() {
            useGradleVersion(version)
        }
        versionRunner.directory = dir.root

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: 'com.github.johnrengelman.shadow'
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'junit:junit:3.8.2' }
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

        when:
        versionRunner.arguments << 'shadowJar'
        ExecutionResult result = versionRunner.run()

        then:
        success(result)
        assert output.exists()

        where:
        version << ['1.11', '1.12']
    }

    def 'shadow copy'() {
        given:
        URL artifact = this.class.classLoader.getResource('test-artifact-1.0-SNAPSHOT.jar')
        URL project = this.class.classLoader.getResource('test-project-1.0-SNAPSHOT.jar')

        buildFile << """
            |task shadow(type: ${ShadowJar.name}) {
            |    destinationDir = buildDir
            |    baseName = 'shadow'
            |    from('${artifact.path}')
            |    from('${project.path}')
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File output = file('build/shadow.jar')
        assert output.exists()
    }

    def 'include project sources'() {
        given:
        file('src/main/java/shadow/Passed.java') << '''
            |package shadow;
            |public class Passed {}
        '''.stripMargin()

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'junit:junit:3.8.2' }
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['shadow/Passed.class', 'junit/framework/Test.class'])

        and:
        doesNotContain(output, ['/'])
    }

    def 'include project dependencies'() {
        given:
        file('settings.gradle') << """
            |include 'client', 'server'
        """.stripMargin()

        file('client/src/main/java/client/Client.java') << """
            |package client;
            |public class Client {}
            |""".stripMargin()

        file('client/build.gradle') << """
            |apply plugin: 'java'
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'junit:junit:3.8.2' }
        """.stripMargin()

        file('server/src/main/java/server/Server.java') << """
            |package server;
            |
            |import client.Client;
            |
            |public class Server {}
        """.stripMargin()

        file('server/build.gradle') << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile project(':client') }
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

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

    def 'do not overwrite files with the same path'() {
        given:
        file('src/main/java/client/Client.java') << """
            |package client;
            |public class Client {}
            |""".stripMargin()

        file('build.gradle') << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'junit:junit:4.11' }
            |dependencies { compile 'org.hamcrest:hamcrest-core:1.3' }
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

        File clientOutput = file('build/libs/shadow.jar')

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(clientOutput, ['LICENSE_junit-4.11.txt',
                               'LICENSE_hamcrest-core-1.3.txt'])
    }

    def 'depend on project shadow jar'() {
        given:
        file('settings.gradle') << """
            |include 'client', 'server'
        """.stripMargin()

        file('client/src/main/java/client/Client.java') << """
            |package client;
            |public class Client {}
            |""".stripMargin()

        file('client/build.gradle') << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'junit:junit:3.8.2' }
            |
            |shadowJar {
            |   relocate 'junit.framework', 'client.junit.framework'
            |}
        """.stripMargin()

        file('server/src/main/java/server/Server.java') << """
            |package server;
            |
            |import client.Client;
            |import client.junit.framework.Test;
            |
            |public class Server {}
        """.stripMargin()

        file('server/build.gradle') << """
            |apply plugin: 'java'
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile project(path: ':client', configuration: 'shadow') }
        """.stripMargin()

        File serverOutput = file('server/build/libs/server.jar')

        when:
        runner.arguments << ':server:jar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(serverOutput, [
                'server/Server.class'
        ])

        and:
        doesNotContain(serverOutput, [
                'client/Client.class',
                'junit/framework/Test.class',
                'client/junit/framework/Test.class'
        ])
    }

    def 'shadow a project shadow jar'() {
        given:
        file('settings.gradle') << """
            |include 'client', 'server'
        """.stripMargin()

        file('client/src/main/java/client/Client.java') << """
            |package client;
            |public class Client {}
            |""".stripMargin()

        file('client/build.gradle') << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'junit:junit:3.8.2' }
            |
            |shadowJar {
            |   relocate 'junit.framework', 'client.junit.framework'
            |}
        """.stripMargin()

        file('server/src/main/java/server/Server.java') << """
            |package server;
            |
            |import client.Client;
            |import client.junit.framework.Test;
            |
            |public class Server {}
        """.stripMargin()

        file('server/build.gradle') << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile project(path: ':client', configuration: 'shadow') }
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

        File serverOutput = file('server/build/libs/shadow.jar')

        when:
        runner.arguments << ':server:shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(serverOutput, [
                'client/Client.class',
                'client/junit/framework/Test.class',
                'server/Server.class',
        ])

        and:
        doesNotContain(serverOutput, [
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

        file('src/main/java/shadow/Passed.java') << '''
            |package shadow;
            |public class Passed {}
        '''.stripMargin()

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'shadow:a:1.0' }
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

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
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { maven { url "${repo.uri}" } }
            |
            |dependencies {
            |   runtime 'shadow:a:1.0'
            |   shadow 'shadow:b:1.0'
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

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

    def "default copying strategy"() {
        given:
        AppendableMavenFileRepository repo = repo()

        repo.module('shadow', 'a', '1.0')
                .insertFile('META-INF/MANIFEST.MF', 'MANIFEST A')
                .publish()

        repo.module('shadow', 'b', '1.0')
                .insertFile('META-INF/MANIFEST.MF', 'MANIFEST B')
                .publish()

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { maven { url "${repo.uri}" } }
            |
            |dependencies {
            |   runtime 'shadow:a:1.0'
            |   runtime 'shadow:b:1.0'
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        JarFile jar = new JarFile(output)
        assert jar.entries().collect().size() == 4

        and: 'Test that main manifest file is the default one, not the one from any of jars'
        Attributes attributes = jar.manifest.getMainAttributes()
        String val = attributes.getValue('Manifest-Version')
        assert val == '1.0'
    }

    def "Class-Path in Manifest not added if empty"() {
        given:

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: 'com.github.johnrengelman.shadow'
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'junit:junit:3.8.2' }
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
        JarFile jar = new JarFile(output)
        Attributes attributes = jar.manifest.getMainAttributes()
        assert attributes.getValue('Class-Path') == null
    }

    @Issue('SHADOW-65')
    def "add shadow configuration to Class-Path in Manifest"() {
        given:

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: 'com.github.johnrengelman.shadow'
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { shadow 'junit:junit:3.8.2' }
            |
            |jar {
            |   manifest {
            |       attributes 'Class-Path': 'a.jar'
            |   }
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and: 'SHADOW-65 - combine w/ existing Class-Path'
        JarFile jar = new JarFile(output)
        Attributes attributes = jar.manifest.getMainAttributes()
        String classpath = attributes.getValue('Class-Path')
        assert classpath == 'a.jar lib/junit-3.8.2.jar'

    }
}
