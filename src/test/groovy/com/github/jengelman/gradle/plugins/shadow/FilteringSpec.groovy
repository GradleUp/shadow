package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.functional.ExecutionResult
import spock.lang.Ignore
import spock.lang.Issue

class FilteringSpec extends PluginSpecification {

    AppendableMavenFileRepository repo

    def setup() {
        repo = repo()

        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()
        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        buildFile << """
            |apply plugin: ${ShadowPlugin.name}
            |apply plugin: 'java'
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies {
            |   compile 'shadow:a:1.0'
            |   compile 'shadow:b:1.0'
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

    }

    def 'include all dependencies'() {
        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties', 'a2.properties', 'b.properties'])
    }

    def 'exclude files'() {
        given:
        buildFile << """
            |shadowJar {
            |   exclude 'a2.properties'
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties', 'b.properties'])

        and:
        doesNotContain(output, ['a2.properties'])
    }

    def "exclude dependency"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        buildFile << '''
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |      exclude(dependency('shadow:d:1.0'))
            |   }
            |}
        '''.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])
    }

    @Issue("SHADOW-54")
    def "dependency exclusions affect UP-TO-DATE check"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        buildFile << '''
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |      exclude(dependency('shadow:d:1.0'))
            |   }
            |}
        '''.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])

        when: 'Update build file shadowJar dependency exclusion'
        buildFile.text = buildFile.text.replace('exclude(dependency(\'shadow:d:1.0\'))',
                                                'exclude(dependency(\'shadow:c:1.0\'))')

        result = runner.run()

        then:
        success(result)

        and:
        assert !taskUpToDate(result, 'shadowJar')

        and:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'd.properties'])

        and:
        doesNotContain(output, ['c.properties'])
    }

    @Issue("SHADOW-62")
    @Ignore
    def "project exclusions affect UP-TO-DATE check"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        buildFile << '''
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |      exclude(dependency('shadow:d:1.0'))
            |   }
            |}
        '''.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])

        when: 'Update build file shadowJar dependency exclusion'
        buildFile.text << '''
            |shadowJar {
            |   exclude 'a.properties'
            |}
        '''.stripMargin()

        result = runner.run()

        then:
        success(result)

        and:
        assert !taskUpToDate(result, 'shadowJar')

        and:
        contains(output, ['a2.properties', 'b.properties', 'd.properties'])

        and:
        doesNotContain(output, ['a.properties', 'c.properties'])
    }

    def "include dependency, excluding all others"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        file('src/main/java/shadow/Passed.java') << '''
            |package shadow;
            |public class Passed {}
        '''.stripMargin()

        buildFile << '''
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |       include(dependency('shadow:d:1.0'))
            |   }
            |}
        '''.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['d.properties', 'shadow/Passed.class'])

        and:
        doesNotContain(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])
    }

    def 'filter project dependencies'() {
        given:
        file('settings.gradle') << """
            |include 'client', 'server'
        """.stripMargin()

        file('client/src/main/java/client/Client.java') << """
            |package client;
            |public class Client {}
        """.stripMargin()

        file('client/build.gradle') << """
            |apply plugin: 'java'
            |repositories { jcenter() }
            |dependencies { compile 'junit:junit:3.8.2' }
        """.stripMargin()

        file('server/src/main/java/server/Server.java') << """
            |package server;
            |import client.Client;
            |public class Server {}
        """.stripMargin()

        file('server/build.gradle') << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { jcenter() }
            |dependencies { compile project(':client') }
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |   dependencies {
            |       exclude(project(':client'))
            |   }
            |}
        """.stripMargin()

        File serverOutput = file('server/build/libs/shadow.jar')

        when:
        runner.arguments << ':server:shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        doesNotContain(serverOutput, [
                'client/Client.class',
        ])

        and:
        contains(serverOutput, ['server/Server.class', 'junit/framework/Test.class'])
    }

    def 'exclude a transitive project dependency'() {
        given:
        file('settings.gradle') << """
            |include 'client', 'server'
        """.stripMargin()

        file('client/src/main/java/client/Client.java') << """
            |package client;
            |public class Client {}
        """.stripMargin()

        file('client/build.gradle') << """
            |apply plugin: 'java'
            |repositories { jcenter() }
            |dependencies { compile 'junit:junit:3.8.2' }
        """.stripMargin()

        file('server/src/main/java/server/Server.java') << """
            |package server;
            |import client.Client;
            |public class Server {}
        """.stripMargin()

        file('server/build.gradle') << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { jcenter() }
            |dependencies { compile project(':client') }
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |   dependencies {
            |       exclude(dependency {
            |           it.moduleGroup == 'junit'
            |       })
            |   }
            |}
        """.stripMargin()

        File serverOutput = file('server/build/libs/shadow.jar')

        when:
        runner.arguments << ':server:shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        doesNotContain(serverOutput, [
                'junit/framework/Test.class'
        ])

        and:
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class'])
    }

    //http://mail-archives.apache.org/mod_mbox/ant-user/200506.mbox/%3C001d01c57756$6dc35da0$dc00a8c0@CTEGDOMAIN.COM%3E
    def 'verify exclude precedence over include'() {
        given:
        buildFile << """
            |shadowJar {
            |   include '*.jar'
            |   include '*.properties'
            |   exclude 'a2.properties'
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, ['a.properties', 'b.properties'])

        and:
        doesNotContain(output, ['a2.properties'])
    }
}
