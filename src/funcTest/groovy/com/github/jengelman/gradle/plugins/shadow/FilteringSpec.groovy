package com.github.jengelman.gradle.plugins.shadow

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

class FilteringSpec extends BasePluginSpecification {

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

    def 'include all dependencies'() {
        when:
        run('shadowJar')

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties'])
    }

    def 'exclude files'() {
        given:
        buildFile << """
            $shadowJar {
               exclude 'a2.properties'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
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

        buildFile << """
            dependencies {
               implementation 'shadow:d:1.0'
            }

            $shadowJar {
               dependencies {
                  exclude(dependency('shadow:d:1.0'))
               }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])
    }

    @Issue('SHADOW-83')
    def "exclude dependency using wildcard syntax"() {
        given:
        repo.module('shadow', 'c', '1.0')
            .insertFile('c.properties', 'c')
            .publish()
        repo.module('shadow', 'd', '1.0')
            .insertFile('d.properties', 'd')
            .dependsOn('c')
            .publish()

        buildFile << """
            dependencies {
               implementation 'shadow:d:1.0'
            }

            $shadowJar {
               dependencies {
                  exclude(dependency('shadow:d:.*'))
               }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
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

        buildFile << """
            dependencies {
               implementation 'shadow:d:1.0'
            }

            $shadowJar {
               dependencies {
                  exclude(dependency('shadow:d:1.0'))
               }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])

        when: 'Update build file shadowJar dependency exclusion'
        buildFile.text = buildFile.text.replace('exclude(dependency(\'shadow:d:1.0\'))',
            'exclude(dependency(\'shadow:c:1.0\'))')

        BuildResult result = run('shadowJar')

        then:
        assert result.task(':shadowJar').outcome == TaskOutcome.SUCCESS

        and:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'd.properties'])

        and:
        doesNotContain(output, ['c.properties'])
    }

    @Issue("SHADOW-62")
    def "project exclusions affect UP-TO-DATE check"() {
        given:
        repo.module('shadow', 'c', '1.0')
            .insertFile('c.properties', 'c')
            .publish()
        repo.module('shadow', 'd', '1.0')
            .insertFile('d.properties', 'd')
            .dependsOn('c')
            .publish()

        buildFile << """
            dependencies {
               implementation 'shadow:d:1.0'
            }

            $shadowJar {
               dependencies {
                  exclude(dependency('shadow:d:1.0'))
               }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])

        when: 'Update build file shadowJar dependency exclusion'
        buildFile.text = buildFile.text.replace('exclude(dependency(\'shadow:d:1.0\'))',
            'exclude \'a.properties\'')

        BuildResult result = run('shadowJar')

        then:
        assert result.task(':shadowJar').outcome == TaskOutcome.SUCCESS

        and:
        contains(output, ['a2.properties', 'b.properties', 'c.properties', 'd.properties'])

        and:
        doesNotContain(output, ['a.properties'])
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
            package shadow;
            public class Passed {}
        '''.stripIndent()

        buildFile << """
            dependencies {
               implementation 'shadow:d:1.0'
            }

            $shadowJar {
               dependencies {
                   include(dependency('shadow:d:1.0'))
               }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, ['d.properties', 'shadow/Passed.class'])

        and:
        doesNotContain(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])
    }

    def 'filter project dependencies'() {
        given:
        buildFile.text = ''

        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            ${getDefaultBuildScript('java', false, true)}
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            import client.Client;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            ${getDefaultBuildScript('java', false, true)}

            dependencies {
              implementation project(':client')
            }

            $shadowJar {
               dependencies {
                   exclude(project(':client'))
               }
            }
        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server-1.0-all.jar')

        when:
        run(':server:shadowJar')

        then:
        serverOutput.exists()
        doesNotContain(serverOutput, [
            'client/Client.class',
        ])

        and:
        contains(serverOutput, ['server/Server.class', 'junit/framework/Test.class'])
    }

    def 'exclude a transitive project dependency'() {
        given:
        buildFile.text = ''

        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            ${getDefaultBuildScript('java', false, true)}
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            import client.Client;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            ${getDefaultBuildScript('java', false, true)}
            dependencies { implementation project(':client') }

            $shadowJar {
               dependencies {
                   exclude(dependency {
                       it.moduleGroup == 'junit'
                   })
               }
            }
        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server-1.0-all.jar')

        when:
        run(':server:shadowJar')

        then:
        serverOutput.exists()
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
            $shadowJar {
               include '*.jar'
               include '*.properties'
               exclude 'a2.properties'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, ['a.properties', 'b.properties'])

        and:
        doesNotContain(output, ['a2.properties'])
    }

    @Issue("SHADOW-69")
    def "handle exclude with circular dependency"() {
        given:
        repo.module('shadow', 'c', '1.0')
            .insertFile('c.properties', 'c')
            .dependsOn('d')
            .publish()
        repo.module('shadow', 'd', '1.0')
            .insertFile('d.properties', 'd')
            .dependsOn('c')
            .publish()

        buildFile << """
            dependencies {
               implementation 'shadow:d:1.0'
            }

            $shadowJar {
               dependencies {
                  exclude(dependency('shadow:d:1.0'))
               }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])
    }

}
