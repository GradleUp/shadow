package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer
import spock.lang.Unroll

class TransformCachingSpec extends AbstractCachingSpec {
    /**
     * Ensure that that caching is disabled when transforms are used
     */
    def 'shadowJar is not cached when custom transforms are used'() {
        given:
        file('src/main/java/server/Server.java') << """
            package server;

            public class Server {}
        """.stripIndent()

        buildFile << """
            import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
            import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
            import org.apache.tools.zip.ZipOutputStream
            import org.gradle.api.file.FileTreeElement

            class CustomTransformer implements Transformer {
                @Override
                boolean canTransformResource(FileTreeElement element) {
                    return false
                }

                @Override
                void transform(TransformerContext context) {

                }

                @Override
                boolean hasTransformedResource() {
                    return false
                }

                @Override
                void modifyOutputStream(ZipOutputStream jos, boolean preserveFileTimestamps) {

                }
            }

            $shadowJar {
                notCompatibleWithConfigurationCache('CustomTransformer is not cacheable')
                transform(CustomTransformer)
            }
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])
    }

    /**
     * Ensure that we get a cache miss when ServiceFileTransformer transforms are added and caching works when ServiceFileTransformer transforms are present
     */
    @Unroll
    def 'shadowJar is cached correctly when using ServiceFileTransformer'() {
        given:
        file('src/main/java/server/Server.java') << """
            package server;

            public class Server {}
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])

        when:
        // Add a transform
        changeConfigurationTo """
            $shadowJar {
               transform(${ServiceFileTransformer.name}) {
                    path = 'META-INF/foo'
               }
            }
        """
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])

        when:
        // Change the transform configuration
        changeConfigurationTo """
            $shadowJar {
               transform(${ServiceFileTransformer.name}) {
                    path = 'META-INF/bar'
               }
            }
        """
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])
    }

    /**
     * Ensure that we get a cache miss when AppendingTransformer transforms are added and caching works when AppendingTransformer transforms are present
     */
    @Unroll
    def 'shadowJar is cached correctly when using AppendingTransformer'() {
        given:
        file('src/main/resources/foo/bar.properties') << "foo=bar"
        file('src/main/java/server/Server.java') << """
            package server;

            public class Server {}
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])

        when:
        // Add a transform
        changeConfigurationTo """
            $shadowJar {
               transform(${AppendingTransformer.name}) {
                    resource = 'foo/bar.properties'
               }
            }
        """
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'foo/bar.properties'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'foo/bar.properties'
        ])

        when:
        // Change the transform configuration
        assert file('src/main/resources/foo/bar.properties').delete()
        file('src/main/resources/foo/baz.properties') << "foo=baz"
        changeConfigurationTo """
            $shadowJar {
               transform(${AppendingTransformer.name}) {
                    resource = 'foo/baz.properties'
               }
            }
        """
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'foo/baz.properties'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'foo/baz.properties'
        ])
    }

    /**
     * Ensure that we get a cache miss when XmlAppendingTransformer transforms are added and caching works when XmlAppendingTransformer transforms are present
     */
    @Unroll
    def 'shadowJar is cached correctly when using XmlAppendingTransformer'() {
        given:
        file('src/main/resources/foo/bar.xml') << "<foo>bar</foo>"
        file('src/main/java/server/Server.java') << """
            package server;

            public class Server {}
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])

        when:
        // Add a transform
        changeConfigurationTo """
            $shadowJar {
               transform(${XmlAppendingTransformer.name}) {
                    resource = 'foo/bar.xml'
               }
            }
        """
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'foo/bar.xml'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'foo/bar.xml'
        ])

        when:
        // Change the transform configuration
        assert file('src/main/resources/foo/bar.xml').delete()
        file('src/main/resources/foo/baz.xml') << "<foo>baz</foo>"
        changeConfigurationTo """
            $shadowJar {
               transform(${AppendingTransformer.name}) {
                    resource = 'foo/baz.xml'
               }
            }
        """
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'foo/baz.xml'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class',
            'foo/baz.xml'
        ])
    }

    /**
     * Ensure that we get a cache miss when GroovyExtensionModuleTransformer transforms are added and caching works when GroovyExtensionModuleTransformer transforms are present
     */
    @Unroll
    def 'shadowJar is cached correctly when using GroovyExtensionModuleTransformer'() {
        given:
        file('src/main/java/server/Server.java') << """
            package server;

            public class Server {}
        """.stripIndent()

        when:
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])

        when:
        // Add a transform
        changeConfigurationTo """
            $shadowJar {
               transform(${GroovyExtensionModuleTransformer.name})
            }
        """
        assertShadowJarExecutes()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])

        when:
        assertShadowJarIsCachedAndRelocatable()

        then:
        output.exists()
        assertContains(output, [
            'server/Server.class'
        ])
    }
}
