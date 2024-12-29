package com.github.jengelman.gradle.plugins.shadow.caching

class RelocationCachingSpec extends AbstractCachingSpec {
    /**
     * Ensure that we get a cache miss when relocation changes and that caching works with relocation
     */
    def 'shadowJar is cached correctly when relocation is added'() {
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
        outputShadowJar.exists()
        assertContains(outputShadowJar, [
            'server/Server.class',
            'junit/framework/Test.class'
        ])

        when:
        changeConfigurationTo """
            dependencies { implementation 'junit:junit:3.8.2' }

            $shadowJar {
               relocate 'junit.framework', 'foo.junit.framework'
            }
        """
        assertShadowJarExecutes()

        then:
        outputShadowJar.exists()
        assertContains(outputShadowJar, [
            'server/Server.class',
            'foo/junit/framework/Test.class'
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
            'server/Server.class',
            'foo/junit/framework/Test.class'
        ])

        and:
        assertDoesNotContain(outputShadowJar, [
            'junit/framework/Test.class'
        ])
    }
}
