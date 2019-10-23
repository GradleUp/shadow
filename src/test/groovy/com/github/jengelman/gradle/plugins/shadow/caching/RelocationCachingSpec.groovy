package com.github.jengelman.gradle.plugins.shadow.caching

import org.gradle.testkit.runner.BuildResult

import static org.gradle.testkit.runner.TaskOutcome.*

class RelocationCachingSpec extends AbstractCachingSpec {
    /**
     * Ensure that we get a cache miss when relocation changes and that caching works with relocation
     */
    def 'shadowJar is cached correctly when relocation is added'() {
        given:
        buildFile << """
            dependencies { compile 'junit:junit:3.8.2' }
        """.stripIndent()

        file('src/main/java/server/Server.java') << """
            package server;

            import junit.framework.Test;

            public class Server {}
        """.stripIndent()

        when:
        assertShadowJarNotCached()

        then:
        output.exists()
        contains(output, [
                'server/Server.class',
                'junit/framework/Test.class'
        ])

        when:
        changeConfigurationTo """
            dependencies { compile 'junit:junit:3.8.2' }

            shadowJar {
               relocate 'junit.framework', 'foo.junit.framework'
            }
        """
        assertShadowJarNotCached()

        then:
        output.exists()
        contains(output, [
                'server/Server.class',
                'foo/junit/framework/Test.class'
        ])

        and:
        doesNotContain(output, [
                'junit/framework/Test.class'
        ])

        when:
        assertShadowJarCached()

        then:
        output.exists()
        contains(output, [
                'server/Server.class',
                'foo/junit/framework/Test.class'
        ])

        and:
        doesNotContain(output, [
                'junit/framework/Test.class'
        ])
    }
}
