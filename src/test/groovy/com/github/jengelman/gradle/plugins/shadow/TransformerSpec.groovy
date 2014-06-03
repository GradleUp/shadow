package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.functional.ExecutionResult

import java.util.jar.JarInputStream
import java.util.jar.Manifest

class TransformerSpec extends PluginSpecification {

    def 'service resource transformer'() {
        given:
        File one = buildJar('one.jar').insertFile('META-INF/services/org.apache.maven.Shade',
                'one # NOTE: No newline terminates this line/file').write()

        File two = buildJar('two.jar').insertFile('META-INF/services/org.apache.maven.Shade',
                'two # NOTE: No newline terminates this line/file').write()

        buildFile << """
            task shadow(type: ${ShadowJar.name}) {
                destinationDir = buildDir
                baseName = 'shadow'
                from('${one.path}')
                from('${two.path}')
                transformer(${ServiceFileTransformer.name})
            }
        """

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File output = file('build/shadow.jar')
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'META-INF/services/org.apache.maven.Shade')
        assert text.split('(\r\n)|(\r)|(\n)').size() == 2
        assert text == '''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file'''
    }

    def 'appending transformer'() {
        given:
        File one = buildJar('one.jar').insertFile('test.properties',
                'one # NOTE: No newline terminates this line/file').write()

        File two = buildJar('two.jar').insertFile('test.properties',
                'two # NOTE: No newline terminates this line/file').write()

        buildFile << """
            task shadow(type: ${ShadowJar.name}) {
                destinationDir = buildDir
                baseName = 'shadow'
                from('${one.path}')
                from('${two.path}')
                transformer(${AppendingTransformer.name}) {
                    resource = 'test.properties'
                }
            }
        """

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File output = file('build/shadow.jar')
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'test.properties')
        assert text.split('(\r\n)|(\r)|(\n)').size() == 2
        assert text == '''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file
'''
    }

    def 'manifest retained'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''package shadow;

public class Main {

    public static void main(String[] args) { }
}'''

        buildFile << """
apply plugin: ${ShadowPlugin.name}

jar {
    manifest {
        attributes 'Main-Class': 'shadow.Main'
        attributes 'Test-Entry': 'PASSED'
    }
}

shadowJar {
    baseName = 'shadow'
    classifier = null
}
"""

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File output = file('build/libs/shadow.jar')
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
        main << '''package shadow;

public class Main {

    public static void main(String[] args) { }
}'''

        buildFile << """
apply plugin: ${ShadowPlugin.name}

jar {
    manifest {
        attributes 'Main-Class': 'shadow.Main'
        attributes 'Test-Entry': 'FAILED'
    }
}

shadowJar {
    baseName = 'shadow'
    classifier = null
    appendManifest {
        attributes 'Test-Entry': 'PASSED'
        attributes 'New-Entry': 'NEW'
    }
}
"""

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File output = file('build/libs/shadow.jar')
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
        File xml1 = buildJar('xml1.jar').insertFile('properties.xml', '''<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">

<properties version="1.0">
  <entry key="key1">val1</entry>
</properties>
''').write()

        File xml2 = buildJar('xml2.jar').insertFile('properties.xml', '''<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">

<properties version="1.0">
  <entry key="key2">val2</entry>
</properties>
''').write()

        buildFile << """
task shadow(type: ${ShadowJar.name}) {
    destinationDir = buildDir
    baseName = 'shadow'
    from('${xml1.path}')
    from('${xml2.path}')
    transformer(${XmlAppendingTransformer.name}) {
        resource = 'properties.xml'
    }
}
"""

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File output = file('build/shadow.jar')
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'properties.xml')
        assert text.replaceAll('\r\n', '\n') == '''<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">

<properties version="1.0">
  <entry key="key1">val1</entry>
  <entry key="key2">val2</entry>
</properties>

'''
    }
}
