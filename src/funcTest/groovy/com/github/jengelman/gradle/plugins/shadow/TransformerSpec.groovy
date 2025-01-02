package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import spock.lang.Issue
import spock.lang.Unroll

import java.util.jar.JarInputStream
import java.util.jar.Manifest

class TransformerSpec extends BasePluginSpecification {
    @Issue('https://github.com/GradleUp/shadow/issues/82')
    def 'shadow manifest leaks to jar manifest'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''
            package shadow;

            public class Main {

               public static void main(String[] args) { }
            }
        '''.stripIndent()

        projectScriptFile << """
            jar {
               manifest {
                   attributes 'Main-Class': 'shadow.Main'
                   attributes 'Test-Entry': 'FAILED'
               }
            }

            $shadowJar {
               manifest {
                   attributes 'Test-Entry': 'PASSED'
                   attributes 'New-Entry': 'NEW'
               }
            }
        """.stripIndent()

        when:
        run('jar', 'shadowJar')

        then:
        File jar = file('build/libs/shadow-1.0.jar')
        assert jar.exists()
        assert outputShadowJar.exists()

        then: 'Check contents of Shadow jar manifest'
        JarInputStream jis = new JarInputStream(outputShadowJar.newInputStream())
        Manifest mf = jis.manifest

        assert mf
        assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Main-Class') == 'shadow.Main'
        assert mf.mainAttributes.getValue('New-Entry') == 'NEW'

        then: 'Check contents of jar manifest'
        JarInputStream jis2 = new JarInputStream(jar.newInputStream())
        Manifest mf2 = jis2.manifest

        assert mf2
        assert mf2.mainAttributes.getValue('Test-Entry') == 'FAILED'
        assert mf2.mainAttributes.getValue('Main-Class') == 'shadow.Main'
        assert !mf2.mainAttributes.getValue('New-Entry')

        cleanup:
        jis?.close()
        jis2?.close()
    }

    def 'Groovy extension module transformer'() {
        given:
        def one = buildJar('one.jar')
            .insert('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
                '''moduleName=foo
moduleVersion=1.0.5
extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
staticExtensionClasses=com.acme.foo.FooStaticExtension'''.stripIndent()).write()

        def two = buildJar('two.jar')
            .insert('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
                '''moduleName=bar
moduleVersion=2.3.5
extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
staticExtensionClasses=com.acme.bar.SomeStaticExtension'''.stripIndent()).write()

        projectScriptFile << """
                import ${GroovyExtensionModuleTransformer.name}
                $shadowJar {
                    from('${escapedPath(one)}')
                    from('${escapedPath(two)}')
                    transform(GroovyExtensionModuleTransformer)
                }
            """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert outputShadowJar.exists()

        and:
        def text = getJarFileContents(outputShadowJar, 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule')
        def props = new Properties()
        props.load(new StringReader(text))
        assert props.getProperty('moduleName') == 'MergedByShadowJar'
        assert props.getProperty('moduleVersion') == '1.0.0'
        assert props.getProperty('extensionClasses') == 'com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension'
        assert props.getProperty('staticExtensionClasses') == 'com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension'
    }

    def 'Groovy extension module transformer works for Groovy2_5+'() {
        given:
        def one = buildJar('one.jar')
            .insert('META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule',
                '''moduleName=foo
moduleVersion=1.0.5
extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
staticExtensionClasses=com.acme.foo.FooStaticExtension'''.stripIndent()).write()

        def two = buildJar('two.jar')
            .insert('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
                '''moduleName=bar
moduleVersion=2.3.5
extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
staticExtensionClasses=com.acme.bar.SomeStaticExtension'''.stripIndent()).write()

        projectScriptFile << """
                import ${GroovyExtensionModuleTransformer.name}
                $shadowJar {
                    from('${escapedPath(one)}')
                    from('${escapedPath(two)}')
                    transform(GroovyExtensionModuleTransformer)
                }
            """.stripIndent()

        when:
        run('shadowJar')

        then:
        outputShadowJar.exists()

        and:
        def text = getJarFileContents(outputShadowJar, 'META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule')
        def props = new Properties()
        props.load(new StringReader(text))
        props.getProperty('moduleName') == 'MergedByShadowJar'
        props.getProperty('moduleVersion') == '1.0.0'
        props.getProperty('extensionClasses') == 'com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension'
        props.getProperty('staticExtensionClasses') == 'com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension'
        assertDoesNotContain(outputShadowJar, ['META-INF/services/org.codehaus.groovy.runtime.ExtensionModule'])
    }

    def 'Groovy extension module transformer short syntax'() {
        given:
        def one = buildJar('one.jar')
            .insert('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
                '''moduleName=foo
moduleVersion=1.0.5
extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
staticExtensionClasses=com.acme.foo.FooStaticExtension'''.stripIndent()).write()

        def two = buildJar('two.jar')
            .insert('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
                '''moduleName=bar
moduleVersion=2.3.5
extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
staticExtensionClasses=com.acme.bar.SomeStaticExtension'''.stripIndent()).write()

        projectScriptFile << """
                $shadowJar {
                    from('${escapedPath(one)}')
                    from('${escapedPath(two)}')
                    mergeGroovyExtensionModules()
                }
            """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert outputShadowJar.exists()

        and:
        def text = getJarFileContents(outputShadowJar, 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule')
        def props = new Properties()
        props.load(new StringReader(text))
        assert props.getProperty('moduleName') == 'MergedByShadowJar'
        assert props.getProperty('moduleVersion') == '1.0.0'
        assert props.getProperty('extensionClasses') == 'com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension'
        assert props.getProperty('staticExtensionClasses') == 'com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension'
    }

    @Unroll
    def '#transformer should not have deprecated behaviours'() {
        given:
        if (configuration.contains('test/some.file')) {
            file('test/some.file') << 'some content'
        }
        projectScriptFile << """
            import com.github.jengelman.gradle.plugins.shadow.transformers.${transformer}

            $shadowJar {
                transform(${transformer})${configuration}
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert outputShadowJar.exists()

        where:
        transformer                         | configuration
        'ApacheLicenseResourceTransformer'  | ''
        'ApacheNoticeResourceTransformer'   | ''
        'AppendingTransformer'              | ''
        'ComponentsXmlResourceTransformer'  | ''
        'DontIncludeResourceTransformer'    | ''
        'GroovyExtensionModuleTransformer'  | ''
        'IncludeResourceTransformer'        | '{ resource.set("test.file"); file.fileValue(file("test/some.file")) }'
        'Log4j2PluginsCacheFileTransformer' | ''
        'ManifestAppenderTransformer'       | ''
        'ManifestResourceTransformer'       | ''
        'PropertiesFileTransformer'         | '{ keyTransformer = { it.toLowerCase() } }'
        'ServiceFileTransformer'            | ''
        'XmlAppendingTransformer'           | ''
    }
}
