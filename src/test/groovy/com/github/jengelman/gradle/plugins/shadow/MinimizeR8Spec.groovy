package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification

class MinimizeR8Spec extends PluginSpecification {

    /**
     * 'Server' depends on 'Client'. 'junit' is independent.
     * The minimize shall remove 'junit'.
     */
    def 'minimize by keeping only transitive dependencies'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;

            import client.Client;

            public class Server {
                private final String client = Client.class.getName();
            }
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                useR8(true)
                minimize()
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar', '--stacktrace')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class'
        ])
        doesNotContain(serverOutput, ['junit/framework/Test.class'])
    }

    /**
     * 'Client', 'Server' and 'junit' are independent.
     * 'junit' is excluded from the minimize.
     * The minimize shall remove 'Client' but not 'junit'.
     */
    def 'exclude a dependency from minimize'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                useR8(true)
                minimize {
                    exclude(dependency('junit:junit:.*'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar', '--stacktrace')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'server/Server.class',
                'junit/framework/Test.class'
        ])
        doesNotContain(serverOutput, ['client/Client.class'])
    }

    /**
     * 'Client', 'Server' and 'junit' are independent.
     * Unused classes of 'client' and theirs dependencies shouldn't be removed.
     */
    def 'exclude a project from minimize '() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                useR8(true)
                minimize {
                    exclude(project(':client'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = file('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar', '--stacktrace')

        then:
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class'
        ])
    }

    /**
     * 'Client', 'Server' and 'junit' are independent.
     * Unused classes of 'client' and theirs dependencies shouldn't be removed.
     */
    def 'exclude a project from minimize - shall not exclude transitive dependencies that are used in subproject'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            import junit.framework.TestCase;
            public class Client extends TestCase {
                public static void main(String[] args) {} 
            }
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                useR8(true)
                minimize {
                    exclude(project(':client'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = file('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar', '--stacktrace')

        then:
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class',
                'junit/framework/TestCase.class'
        ])
    }

    /**
     * 'Client', 'Server' and 'junit' are independent.
     * Unused classes of 'client' and theirs dependencies shouldn't be removed.
     */
    def 'exclude a project from minimize - shall not exclude transitive dependencies from subproject that are not used'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client { }
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                useR8(true)
                minimize {
                    exclude(project(':client'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = file('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar', '--stacktrace')

        then:
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class',
                'junit/framework/TestCase.class'
        ])
    }


    /**
     * 'api' used as api for 'impl', and depended on 'lib'. 'junit' is independent.
     * The minimize shall remove 'junit', but not 'api'.
     * Unused classes of 'api' and their dependencies also shouldn't be removed.
     * Transitive dependencies (implementation scope) that are not part of the public api
     * of classes in 'api' are not kept.
     */
    def 'use minimize with dependencies with api scope'() {
        given:
        file('settings.gradle') << """
            include 'api', 'lib', 'impl'
        """.stripIndent()

        file('lib/src/main/java/lib/LibEntity.java') << """
            package lib;
            public interface LibEntity {}
        """.stripIndent()

        file('lib/src/main/java/lib/UnusedLibEntity.java') << """
            package lib;
            public class UnusedLibEntity implements LibEntity {}
        """.stripIndent()

        file('lib/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
        """.stripIndent()

        file('api/src/main/java/api/Entity.java') << """
            package api;
            public interface Entity {}
        """.stripIndent()

        file('api/src/main/java/api/UnusedEntity.java') << """
            package api;
            import lib.LibEntity;
            public class UnusedEntity implements LibEntity {}
        """.stripIndent()

        file('api/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies {
                implementation 'junit:junit:3.8.2'
                implementation project(':lib')
            }
        """.stripIndent()

        file('impl/src/main/java/impl/SimpleEntity.java') << """
            package impl;
            import api.Entity;
            public class SimpleEntity implements Entity {}
        """.stripIndent()

        file('impl/build.gradle') << """
            apply plugin: 'java-library'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                useR8(true)
                minimize()
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { api project(':api') }
        """.stripIndent()

        File serverOutput = getFile('impl/build/libs/impl-all.jar')

        when:
        runWithDebug(':impl:shadowJar', '--stacktrace')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'impl/SimpleEntity.class',
                'api/Entity.class',
                'api/UnusedEntity.class'
        ])
        doesNotContain(serverOutput, ['junit/framework/Test.class', 'lib/UnusedLibEntity.class'])
    }

    /**
     * 'api' used as api for 'impl', and depended on 'lib'. 'junit' is independent.
     * The minimize shall remove 'junit', but not 'api'.
     * Unused classes of 'api' and their (used) dependencies also shouldn't be removed.
     * Transitive dependencies (implementation scope) that are part of the public api
     * of classes in 'api' are kept as well.
     */
    def 'use minimize with dependencies with api scope, descriptorclasses kept'() {
        given:
        file('settings.gradle') << """
            include 'api', 'lib', 'impl'
        """.stripIndent()

        file('lib/src/main/java/lib/LibEntity.java') << """
            package lib;
            public interface LibEntity {
                void simple();
            }
        """.stripIndent()

        file('lib/src/main/java/lib/UnusedLibEntity.java') << """
            package lib;
            public class UnusedLibEntity implements LibEntity {
                public void simple() {}
            }
        """.stripIndent()

        file('lib/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
        """.stripIndent()

        file('api/src/main/java/api/Entity.java') << """
            package api;
            public interface Entity {}
        """.stripIndent()

        file('api/src/main/java/api/UnusedEntity.java') << """
            package api;
            import lib.LibEntity;
            public class UnusedEntity implements LibEntity {
                public void simple() {}
                public void useLib(LibEntity entity) {
                    entity.simple();
                }
            }
        """.stripIndent()

        file('api/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies {
                implementation 'junit:junit:3.8.2'
                implementation project(':lib')
            }
        """.stripIndent()

        file('impl/src/main/java/impl/SimpleEntity.java') << """
            package impl;
            import api.Entity;
            public class SimpleEntity implements Entity {}
        """.stripIndent()

        file('impl/build.gradle') << """
            apply plugin: 'java-library'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                useR8(true)
                minimize()
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { api project(':api') }
        """.stripIndent()

        File serverOutput = getFile('impl/build/libs/impl-all.jar')

        when:
        runWithDebug(':impl:shadowJar', '--stacktrace')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'impl/SimpleEntity.class',
                'api/Entity.class',
                'api/UnusedEntity.class',
                'lib/LibEntity.class'
        ])
        doesNotContain(serverOutput, ['junit/framework/Test.class', 'lib/UnusedLibEntity.class'])
    }

    /**
     * 'api' used as api for 'impl', and 'lib' used as api for 'api'.
     * Unused classes of 'api' and 'lib' shouldn't be removed.
     */
    def 'use minimize with transitive dependencies with api scope'() {
        given:
        file('settings.gradle') << """
            include 'api', 'lib', 'impl'
        """.stripIndent()

        file('lib/src/main/java/lib/LibEntity.java') << """
            package lib;
            public interface LibEntity {}
        """.stripIndent()

        file('lib/src/main/java/lib/UnusedLibEntity.java') << """
            package lib;
            public class UnusedLibEntity implements LibEntity {}
        """.stripIndent()

        file('lib/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
        """.stripIndent()

        file('api/src/main/java/api/Entity.java') << """
            package api;
            public interface Entity {}
        """.stripIndent()

        file('api/src/main/java/api/UnusedEntity.java') << """
            package api;
            import lib.LibEntity;
            public class UnusedEntity implements LibEntity {}
        """.stripIndent()

        file('api/build.gradle') << """
            apply plugin: 'java-library'
            repositories { maven { url "${repo.uri}" } }
            dependencies { api project(':lib') }
        """.stripIndent()

        file('impl/src/main/java/impl/SimpleEntity.java') << """
            package impl;
            import api.Entity;
            public class SimpleEntity implements Entity {}
        """.stripIndent()

        file('impl/build.gradle') << """
            apply plugin: 'java-library'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                useR8(true)
                minimize()
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { api project(':api') }
        """.stripIndent()

        File serverOutput = getFile('impl/build/libs/impl-all.jar')

        when:
        runWithDebug(':impl:shadowJar', '--stacktrace')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'impl/SimpleEntity.class',
                'api/Entity.class',
                'api/UnusedEntity.class',
                'lib/LibEntity.class',
                'lib/UnusedLibEntity.class'
        ])
    }
}
