package com.github.jengelman.gradle.plugins.shadow.caching

class ShadowJarCachingSpec extends AbstractCachingSpec {

    /**
     * Ensure that a basic usage reuses an output from cache and then gets a cache miss when the content changes.
     */
    def "shadowJar is cached correctly when copying"() {
        given:
        URL artifact = this.class.classLoader.getResource('test-artifact-1.0-SNAPSHOT.jar')
        URL project = this.class.classLoader.getResource('test-project-1.0-SNAPSHOT.jar')

        projectScriptFile << """
            $shadowJar {
                from('${artifact.path}')
                from('${project.path}')
            }
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        assert outputShadowJar.exists()

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        assert outputShadowJar.exists()

        when:
        changeConfigurationTo """
            $shadowJar {
                from('${artifact.path}')
            }
        """
        assertShadowJarExecutes()

        then:
        assert outputShadowJar.exists()
    }

    /**
     * Ensure that an output is reused from the cache if only the output file name is changed.
     */
    def "shadowJar is cached correctly when output file is changed"() {
        given:
        URL artifact = this.class.classLoader.getResource('test-artifact-1.0-SNAPSHOT.jar')
        URL project = this.class.classLoader.getResource('test-project-1.0-SNAPSHOT.jar')

        projectScriptFile << """
            $shadowJar {
                from('${artifact.path}')
                from('${project.path}')
            }
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        assert outputShadowJar.exists()

        when:
        changeConfigurationTo """
            $shadowJar {
                archiveBaseName = "foo"
                from('${artifact.path}')
                from('${project.path}')
            }
        """
        assertShadowJarIsCachedAndRelocatable()

        then:
        assert !outputShadowJar.exists()
        assert file("build/libs/foo-1.0-all.jar").exists()
    }

    /**
     * Ensure that we get a cache miss when includes/excludes change and that caching works when includes/excludes are present
     */
    def 'shadowJar is cached correctly when using includes/excludes'() {
        given:
        projectScriptFile << """
            dependencies { implementation 'junit:junit:3.8.2' }

            $shadowJar {
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
        outputShadowJar.exists()
        assertContains(outputShadowJar, [
            'server/Server.class',
            'server/Util.class'
        ])

        when:
        changeConfigurationTo """
            dependencies { implementation 'junit:junit:3.8.2' }

            $shadowJar {
               include 'server/*'
               exclude '*/Util.*'
            }
        """
        assertShadowJarExecutes()

        then:
        outputShadowJar.exists()
        assertContains(outputShadowJar, [
            'server/Server.class'
        ])

        and:
        assertDoesNotContain(outputShadowJar, [
            'server/Util.class',
            'junit/framework/Test.class'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        outputShadowJar.exists()
        assertContains(outputShadowJar, [
            'server/Server.class'
        ])

        and:
        assertDoesNotContain(outputShadowJar, [
            'server/Util.class',
            'junit/framework/Test.class'
        ])
    }

    /**
     * Ensure that we get a cache miss when dependency includes/excludes are added and caching works when dependency includes/excludes are present
     */
    def 'shadowJar is cached correctly when using dependency includes/excludes'() {
        given:
        projectScriptFile << """
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
        outputShadowJar.exists()
        assertContains(outputShadowJar, [
            'server/Server.class',
            'junit/framework/Test.class'
        ])

        when:
        changeConfigurationTo """
            dependencies { implementation 'junit:junit:3.8.2' }

            $shadowJar {
               dependencies {
                    exclude(dependency('junit:junit'))
               }
            }
        """
        assertShadowJarExecutes()

        then:
        outputShadowJar.exists()
        assertContains(outputShadowJar, [
            'server/Server.class'
        ])

        and:
        assertDoesNotContain(outputShadowJar, [
            'junit/framework/Test.class'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        outputShadowJar.exists()
        assertContains(outputShadowJar, [
            'server/Server.class'
        ])

        and:
        assertDoesNotContain(outputShadowJar, [
            'junit/framework/Test.class'
        ])
    }
}
