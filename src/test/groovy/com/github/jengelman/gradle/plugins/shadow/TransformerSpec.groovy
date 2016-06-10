package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import spock.lang.Issue

import java.util.jar.JarInputStream
import java.util.jar.Manifest

class TransformerSpec extends PluginSpecification {

    def 'service resource transformer'() {
        given:
        File one = buildJar('one.jar')
                .insertFile('META-INF/services/org.apache.maven.Shade',
                        'one # NOTE: No newline terminates this line/file')
                .insertFile('META-INF/services/com.acme.Foo', 'one')
                .write()

        File two = buildJar('two.jar')
                .insertFile('META-INF/services/org.apache.maven.Shade',
                        'two # NOTE: No newline terminates this line/file')
                .insertFile('META-INF/services/com.acme.Foo', 'two')
                .write()

        buildFile << """
            import ${ServiceFileTransformer.name}
            shadowJar {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            shadowJar {
                transform(ServiceFileTransformer) {
                    exclude 'META-INF/services/com.acme.*'
                }
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

        then:
        assert output.exists()

        and:
        String text1 = getJarFileContents(output, 'META-INF/services/org.apache.maven.Shade')
        assert text1.split("\\r?\\n").size() == 2
        assert text1 ==
'''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file'''.stripIndent()

        and:
        String text2 = getJarFileContents(output, 'META-INF/services/com.acme.Foo')
        assert text2.split("\\r?\\n").size() == 1
        assert text2 == 'one'
    }

    def 'service resource transformer alternate path'() {
        given:
            File one = buildJar('one.jar').insertFile('META-INF/foo/org.apache.maven.Shade',
                    'one # NOTE: No newline terminates this line/file').write()

            File two = buildJar('two.jar').insertFile('META-INF/foo/org.apache.maven.Shade',
                    'two # NOTE: No newline terminates this line/file').write()

            buildFile << """
            import ${ServiceFileTransformer.name}
            shadowJar {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            shadowJar {
                transform(ServiceFileTransformer) {
                    path = 'META-INF/foo'
                }
            }
        """.stripIndent()

        when:
            runner.withArguments('shadowJar').build()

        then:
            assert output.exists()

        and:
            String text = getJarFileContents(output, 'META-INF/foo/org.apache.maven.Shade')
            assert text.split("\\r?\\n").size() == 2
            assert text ==
'''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file'''.stripIndent()
    }

    def 'service resource transformer short syntax'() {
        given:
            File one = buildJar('one.jar')
                    .insertFile('META-INF/services/org.apache.maven.Shade',
                    'one # NOTE: No newline terminates this line/file')
                    .insertFile('META-INF/services/com.acme.Foo', 'one')
                    .write()

            File two = buildJar('two.jar')
                    .insertFile('META-INF/services/org.apache.maven.Shade',
                    'two # NOTE: No newline terminates this line/file')
                    .insertFile('META-INF/services/com.acme.Foo', 'two')
                    .write()

        buildFile << """
            shadowJar {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            shadowJar {
                mergeServiceFiles {
                    exclude 'META-INF/services/com.acme.*'
                }
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

        then:
        assert output.exists()

        and:
            String text1 = getJarFileContents(output, 'META-INF/services/org.apache.maven.Shade')
            assert text1.split("\\r?\\n").size() == 2
            assert text1 ==
'''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file'''.stripIndent()

        and:
            String text2 = getJarFileContents(output, 'META-INF/services/com.acme.Foo')
            assert text2.split("\\r?\\n").size() == 1
            assert text2 == 'one'
    }

    def 'service resource transformer short syntax relocation'() {
        given:
        File one = buildJar('one.jar')
                .insertFile('META-INF/services/java.sql.Driver',
'''oracle.jdbc.OracleDriver
org.apache.hive.jdbc.HiveDriver'''.stripIndent())
                .insertFile('META-INF/services/org.apache.axis.components.compiler.Compiler',
                'org.apache.axis.components.compiler.Javac')
                .insertFile('META-INF/services/org.apache.commons.logging.LogFactory',
                'org.apache.commons.logging.impl.LogFactoryImpl')
                .write()

        File two = buildJar('two.jar')
                .insertFile('META-INF/services/java.sql.Driver',
'''org.apache.derby.jdbc.AutoloadedDriver
com.mysql.jdbc.Driver'''.stripIndent())
                .insertFile('META-INF/services/org.apache.axis.components.compiler.Compiler',
                'org.apache.axis.components.compiler.Jikes')
                .insertFile('META-INF/services/org.apache.commons.logging.LogFactory',
                'org.mortbay.log.Factory')
                .write()

        buildFile << """
            shadowJar {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            shadowJar {
                mergeServiceFiles()
                relocate('org.apache', 'myapache') {
                    exclude 'org.apache.axis.components.compiler.Jikes'
                    exclude 'org.apache.commons.logging.LogFactory'
                }
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

        then:
        assert output.exists()

        and:
        String text1 = getJarFileContents(output, 'META-INF/services/java.sql.Driver')
        assert text1.split("\\r?\\n").size() == 4
        assert text1 ==
'''oracle.jdbc.OracleDriver
myapache.hive.jdbc.HiveDriver
myapache.derby.jdbc.AutoloadedDriver
com.mysql.jdbc.Driver'''.stripIndent()

        and:
        String text2 = getJarFileContents(output, 'META-INF/services/myapache.axis.components.compiler.Compiler')
        assert text2.split("\\r?\\n").size() == 2
        assert text2 ==
'''myapache.axis.components.compiler.Javac
org.apache.axis.components.compiler.Jikes'''.stripIndent()

        and:
        String text3 = getJarFileContents(output, 'META-INF/services/org.apache.commons.logging.LogFactory')
        assert text3.split("\\r?\\n").size() == 2
        assert text3 ==
'''myapache.commons.logging.impl.LogFactoryImpl
org.mortbay.log.Factory'''.stripIndent()
    }

    def 'service resource transformer short syntax alternate path'() {
        given:
            File one = buildJar('one.jar').insertFile('META-INF/foo/org.apache.maven.Shade',
                    'one # NOTE: No newline terminates this line/file').write()

            File two = buildJar('two.jar').insertFile('META-INF/foo/org.apache.maven.Shade',
                    'two # NOTE: No newline terminates this line/file').write()

            buildFile << """
            shadowJar {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            shadowJar {
                mergeServiceFiles('META-INF/foo')
            }
        """.stripIndent()

        when:
            runner.withArguments('shadowJar').build()

        then:
            assert output.exists()

        and:
            String text = getJarFileContents(output, 'META-INF/foo/org.apache.maven.Shade')
            assert text.split("\\r?\\n").size() == 2
            assert text ==
'''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file'''.stripIndent()
    }

    @Issue(['SHADOW-70', 'SHADOW-71'])
    def 'apply transformers to project resources'() {
        given:
        File one = buildJar('one.jar').insertFile('META-INF/services/shadow.Shadow',
                'one # NOTE: No newline terminates this line/file').write()

        repo.module('shadow', 'two', '1.0').insertFile('META-INF/services/shadow.Shadow',
                'two # NOTE: No newline terminates this line/file').publish()

        buildFile << """
            dependencies {
              compile 'shadow:two:1.0'
              compile files('${escapedPath(one)}')
            }
            
            shadowJar {
              mergeServiceFiles()
            }
        """.stripIndent()

        file('src/main/resources/META-INF/services/shadow.Shadow') <<
                'three # NOTE: No newline terminates this line/file'

        when:
        runner.withArguments('shadowJar').build()

        then:
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'META-INF/services/shadow.Shadow')
        assert text.split("\\r?\\n").size() == 3
        assert text ==
'''three # NOTE: No newline terminates this line/file
one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file'''.stripIndent()
    }

    def 'appending transformer'() {
        given:
        File one = buildJar('one.jar').insertFile('test.properties',
                'one # NOTE: No newline terminates this line/file').write()

        File two = buildJar('two.jar').insertFile('test.properties',
                'two # NOTE: No newline terminates this line/file').write()

        buildFile << """
            import ${AppendingTransformer.name}
            shadowJar {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            shadowJar {
                transform(AppendingTransformer) {
                    resource = 'test.properties'
                }
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

        then:
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'test.properties')
        assert text.split("\\r?\\n").size() == 2
        assert text ==
'''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file
'''.stripIndent()
    }

    def 'appending transformer short syntax'() {
        given:
        File one = buildJar('one.jar').insertFile('test.properties',
                'one # NOTE: No newline terminates this line/file').write()

        File two = buildJar('two.jar').insertFile('test.properties',
                'two # NOTE: No newline terminates this line/file').write()

        buildFile << """
            shadowJar {
                from('${escapedPath(one)}')
                from('${escapedPath(two)}')
            }
            shadowJar {
                append('test.properties')
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

        then:
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'test.properties')
        assert text.split("\\r?\\n").size() == 2
        assert text ==
'''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file
'''.stripIndent()
    }

    def 'manifest retained'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''
            package shadow;
            
            public class Main {
            
               public static void main(String[] args) { }
            }
        '''.stripIndent()

        buildFile << """
            jar {
               manifest {
                   attributes 'Main-Class': 'shadow.Main'
                   attributes 'Test-Entry': 'PASSED'
               }
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

        then:
        assert output.exists()

        and:
        JarInputStream jis = new JarInputStream(output.newInputStream())
        Manifest mf = jis.manifest
        jis.close()

        assert mf
        assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Main-Class') == 'shadow.Main'
    }

    def 'manifest transformed'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''
            package shadow;
            
            public class Main {
            
               public static void main(String[] args) { }
            }
        '''.stripIndent()

        buildFile << """
            jar {
               manifest {
                   attributes 'Main-Class': 'shadow.Main'
                   attributes 'Test-Entry': 'FAILED'
               }
            }
            
            shadowJar {
               manifest {
                   attributes 'Test-Entry': 'PASSED'
                   attributes 'New-Entry': 'NEW'
               }
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

        then:
        assert output.exists()

        and:
        JarInputStream jis = new JarInputStream(output.newInputStream())
        Manifest mf = jis.manifest
        jis.close()

        assert mf
        assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Main-Class') == 'shadow.Main'
        assert mf.mainAttributes.getValue('New-Entry') == 'NEW'
    }

    def 'append xml files'() {
        given:
        File xml1 = buildJar('xml1.jar').insertFile('properties.xml',
'''<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
            
<properties version="1.0">
   <entry key="key1">val1</entry>
</properties>
'''.stripIndent()
        ).write()

        File xml2 = buildJar('xml2.jar').insertFile('properties.xml',
'''<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
            
<properties version="1.0">
   <entry key="key2">val2</entry>
</properties>
'''.stripIndent()
        ).write()

        buildFile << """
            import ${XmlAppendingTransformer.name}

            shadowJar {
               from('${escapedPath(xml1)}')
               from('${escapedPath(xml2)}')
            }

            shadowJar {
               transform(XmlAppendingTransformer) {
                   resource = 'properties.xml'
               }
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

        then:
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'properties.xml')
        assert text.replaceAll('\r\n', '\n') ==
'''<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties version="1.0">
  <entry key="key1">val1</entry>
  <entry key="key2">val2</entry>
</properties>
'''.stripIndent()
    }

    @Issue('SHADOW-82')
    def 'shadow.manifest leaks to jar.manifest'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''
            package shadow;
            
            public class Main {
            
               public static void main(String[] args) { }
            }
        '''.stripIndent()

        buildFile << """
            jar {
               manifest {
                   attributes 'Main-Class': 'shadow.Main'
                   attributes 'Test-Entry': 'FAILED'
               }
            }
            
            shadowJar {
               manifest {
                   attributes 'Test-Entry': 'PASSED'
                   attributes 'New-Entry': 'NEW'
               }
            }
        """.stripIndent()

        when:
        runner.withArguments('jar', 'shadowJar').build()

        then:
        File jar = file('build/libs/shadow-1.0.jar')
        assert jar.exists()
        assert output.exists()

        then: 'Check contents of Shadow jar manifest'
        JarInputStream jis = new JarInputStream(output.newInputStream())
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

    @Issue('SHADOW-82')
    def 'shadow manifest leaks to jar manifest'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''
            package shadow;
            
            public class Main {
            
               public static void main(String[] args) { }
            }
        '''.stripIndent()

        buildFile << """
            jar {
               manifest {
                   attributes 'Main-Class': 'shadow.Main'
                   attributes 'Test-Entry': 'FAILED'
               }
            }
            
            shadowJar {
               manifest {
                   attributes 'Test-Entry': 'PASSED'
                   attributes 'New-Entry': 'NEW'
               }
            }
        """.stripIndent()

        when:
        runner.withArguments('jar', 'shadowJar').build()

        then:
        File jar = file('build/libs/shadow-1.0.jar')
        assert jar.exists()
        assert output.exists()

        then: 'Check contents of Shadow jar manifest'
        JarInputStream jis = new JarInputStream(output.newInputStream())
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
                    .insertFile('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
'''moduleName=foo
moduleVersion=1.0.5
extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
staticExtensionClasses=com.acme.foo.FooStaticExtension'''.stripIndent()).write()

            def two = buildJar('two.jar')
                    .insertFile('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
'''moduleName=bar
moduleVersion=2.3.5
extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
staticExtensionClasses=com.acme.bar.SomeStaticExtension'''.stripIndent()).write()

            buildFile << """
                import ${GroovyExtensionModuleTransformer.name}
                shadowJar {
                    from('${escapedPath(one)}')
                    from('${escapedPath(two)}')
                }

                shadowJar {
                    transform(GroovyExtensionModuleTransformer)
                }
            """.stripIndent()

        when:
            runner.withArguments('shadowJar').build()

        then:
            assert output.exists()

        and:
            def text = getJarFileContents(output, 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule')
            def props = new Properties()
            props.load(new StringReader(text))
            assert props.getProperty('moduleName') == 'MergedByShadowJar'
            assert props.getProperty('moduleVersion') == '1.0.0'
            assert props.getProperty('extensionClasses') == 'com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension'
            assert props.getProperty('staticExtensionClasses') == 'com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension'
    }

    def 'Groovy extension module transformer short syntax'() {
        given:
            def one = buildJar('one.jar')
                    .insertFile('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
'''moduleName=foo
moduleVersion=1.0.5
extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
staticExtensionClasses=com.acme.foo.FooStaticExtension'''.stripIndent()).write()

            def two = buildJar('two.jar')
                    .insertFile('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
'''moduleName=bar
moduleVersion=2.3.5
extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
staticExtensionClasses=com.acme.bar.SomeStaticExtension'''.stripIndent()).write()

            buildFile << """
                shadowJar {
                    from('${escapedPath(one)}')
                    from('${escapedPath(two)}')
                }
                shadowJar {
                    mergeGroovyExtensionModules()
                }
            """.stripIndent()

        when:
            runner.withArguments('shadowJar').build()

        then:
            assert output.exists()

        and:
            def text = getJarFileContents(output, 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule')
            def props = new Properties()
            props.load(new StringReader(text))
            assert props.getProperty('moduleName') == 'MergedByShadowJar'
            assert props.getProperty('moduleVersion') == '1.0.0'
            assert props.getProperty('extensionClasses') == 'com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension'
            assert props.getProperty('staticExtensionClasses') == 'com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension'
    }

    private String escapedPath(File file) {
        file.path.replaceAll('\\\\', '\\\\\\\\')
    }
}
