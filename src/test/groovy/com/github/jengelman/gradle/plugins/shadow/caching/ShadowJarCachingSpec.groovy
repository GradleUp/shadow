package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.testkit.runner.BuildResult
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.*

class ShadowJarCachingSpec extends AbstractCachingSpec {

    /**
     * Ensure that a basic usage reuses an output from cache and then gets a cache miss when the content changes.
     */
    def "shadowJar is cached correctly when copying"() {
        given:
        URL artifact = this.class.classLoader.getResource('test-artifact-1.0-SNAPSHOT.jar')
        URL project = this.class.classLoader.getResource('test-project-1.0-SNAPSHOT.jar')

        buildFile << """
            shadowJar {
                from('${artifact.path}')
                from('${project.path}')
            }
        """.stripIndent()

        when:
        assertShadowJarNotCached()

        then:
        assert output.exists()

        when:
        assertShadowJarCached()

        then:
        assert output.exists()

        when:
        changeConfigurationTo """
            shadowJar {
                from('${artifact.path}')
            }
        """
        assertShadowJarNotCached()

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
            shadowJar {
                from('${artifact.path}')
                from('${project.path}')
            }
        """.stripIndent()

        when:
        assertShadowJarNotCached()

        then:
        assert output.exists()

        when:
        changeConfigurationTo """
            shadowJar {
                baseName = "foo"
                from('${artifact.path}')
                from('${project.path}')
            }
        """
        assertShadowJarCached()

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
            dependencies { compile 'junit:junit:3.8.2' }
            
            shadowJar {
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
        assertShadowJarNotCached()

        then:
        output.exists()
        contains(output, [
                'server/Server.class',
                'server/Util.class'
        ])

        when:
        changeConfigurationTo """
            dependencies { compile 'junit:junit:3.8.2' }

            shadowJar {
               include 'server/*'
               exclude '*/Util.*'
            }
        """
        assertShadowJarNotCached()

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
        assertShadowJarCached()

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
               dependencies {
                    exclude(dependency('junit:junit'))
               }
            }
        """
        assertShadowJarNotCached()

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
        assertShadowJarCached()

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
