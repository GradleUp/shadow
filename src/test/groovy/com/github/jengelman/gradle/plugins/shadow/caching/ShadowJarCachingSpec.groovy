package com.github.jengelman.gradle.plugins.shadow.caching

class ShadowJarCachingSpec extends AbstractCachingSpec {

    /**
     * Ensure that a basic usage reuses an output from cache and then gets a cache miss when the content changes.
     */
    def "shadowJar is cached correctly when copying"() {
        given:
        URL artifact = this.class.classLoader.getResource('test-artifact-1.0-SNAPSHOT.jar')
        URL project = this.class.classLoader.getResource('test-project-1.0-SNAPSHOT.jar')

        buildFile << """
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                from('${artifact.path}')
                from('${project.path}')
            }
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        assert output.exists()

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        assert output.exists()

        when:
        changeConfigurationTo """
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                from('${artifact.path}')
            }
        """
        assertShadowJarExecutes()

        then:
        assert output.exists()
    }

    /**
     * Ensure that an output is reused from the cache if only the output file name is changed.
     */
    def "shadowJar is cached correctly when output file is changed"() {
        given:
        URL artifact = this.class.classLoader.getResource('test-artifact-1.0-SNAPSHOT.jar')
        URL project = this.class.classLoader.getResource('test-project-1.0-SNAPSHOT.jar')

        buildFile << """
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                from('${artifact.path}')
                from('${project.path}')
            }
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        assert output.exists()

        when:
        changeConfigurationTo """
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                archiveBaseName = "foo"
                from('${artifact.path}')
                from('${project.path}')
            }
        """
        assertShadowJarIsCachedAndRelocatable()

        then:
        assert !output.exists()
        assert getFile("build/libs/foo-1.0-all.jar").exists()
    }

    /**
     * Ensure that we get a cache miss when includes/excludes change and that caching works when includes/excludes are present
     */
    def 'shadowJar is cached correctly when using includes/excludes'() {
        given:
        buildFile << """
            dependencies { implementation 'junit:junit:3.8.2' }

            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                exclude 'junit/*'
            }
        """.stripIndent()

        file('src/main/java/server/Server.java') << """
            package server;

            import junit.framework.Test;

            public class Server {}
        """.stripIndent()

        file('src/main/java/server/Util.java') << """
            package server;

            import junit.framework.Test;

            public class Util {}
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        contains(output, [
            'server/Server.class',
            'server/Util.class'
        ])

        when:
        changeConfigurationTo """
            dependencies { implementation 'junit:junit:3.8.2' }

            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
               include 'server/*'
               exclude '*/Util.*'
            }
        """
        assertShadowJarExecutes()

        then:
        output.exists()
        contains(output, [
            'server/Server.class'
        ])

        and:
        doesNotContain(output, [
            'server/Util.class',
            'junit/framework/Test.class'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        contains(output, [
            'server/Server.class'
        ])

        and:
        doesNotContain(output, [
            'server/Util.class',
            'junit/framework/Test.class'
        ])
    }

    /**
     * Ensure that we get a cache miss when dependency includes/excludes are added and caching works when dependency includes/excludes are present
     */
    def 'shadowJar is cached correctly when using dependency includes/excludes'() {
        given:
        buildFile << """
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('src/main/java/server/Server.java') << """
            package server;

            import junit.framework.Test;

            public class Server {}
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        contains(output, [
            'server/Server.class',
            'junit/framework/Test.class'
        ])

        when:
        changeConfigurationTo """
            dependencies { implementation 'junit:junit:3.8.2' }

            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
               dependencies {
                    exclude(dependency('junit:junit'))
               }
            }
        """
        assertShadowJarExecutes()

        then:
        output.exists()
        contains(output, [
            'server/Server.class'
        ])

        and:
        doesNotContain(output, [
            'junit/framework/Test.class'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        contains(output, [
            'server/Server.class'
        ])

        and:
        doesNotContain(output, [
            'junit/framework/Test.class'
        ])
    }
}
