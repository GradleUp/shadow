package com.github.jengelman.gradle.plugins.shadow2

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopy
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow2.util.PluginSpecification

class TransformerSpec extends PluginSpecification {

    def 'service resource transformer'() {
        given:
        File one = buildJar('one.jar').insertFile('META-INF/services/org.apache.maven.Shade',
                'one # NOTE: No newline terminates this line/file').write()

        File two = buildJar('two.jar').insertFile('META-INF/services/org.apache.maven.Shade',
                'two # NOTE: No newline terminates this line/file').write()

        buildFile << """
            import ${ShadowCopy.name}

            task shadow(type: ShadowCopy) {
                destinationDir = buildDir
                baseName = 'shadow'
                from('${one.path}')
                from('${two.path}')
                transformer(${ServiceFileTransformer.name})
            }
        """

        when:
        runner.arguments << 'shadow'
        runner.run()

        then:
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
            import ${ShadowCopy.name}

            task shadow(type: ShadowCopy) {
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
        runner.run()

        then:
        File output = file('build/shadow.jar')
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'test.properties')
        assert text.split('(\r\n)|(\r)|(\n)').size() == 2
        assert text == '''one # NOTE: No newline terminates this line/file
two # NOTE: No newline terminates this line/file
'''
    }
}
