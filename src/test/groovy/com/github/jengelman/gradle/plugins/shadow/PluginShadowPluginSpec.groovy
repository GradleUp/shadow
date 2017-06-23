package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification


class PluginShadowPluginSpec extends PluginSpecification {

    def "auto relocate plugin dependencies"() {
        given:
        buildFile.text = buildFile.text.replace('com.github.johnrengelman.shadow', 'com.github.johnrengelman.plugin-shadow')
        buildFile << """
            dependencies {

               compile 'junit:junit:3.8.2'
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar', '-s').build()

        then:
        then:
        contains(output, [
                'META-INF/MANIFEST.MF',
                'shadow/junit/textui/ResultPrinter.class',
                'shadow/junit/textui/TestRunner.class',
                'shadow/junit/framework/Assert.class',
                'shadow/junit/framework/AssertionFailedError.class',
                'shadow/junit/framework/ComparisonCompactor.class',
                'shadow/junit/framework/ComparisonFailure.class',
                'shadow/junit/framework/Protectable.class',
                'shadow/junit/framework/Test.class',
                'shadow/junit/framework/TestCase.class',
                'shadow/junit/framework/TestFailure.class',
                'shadow/junit/framework/TestListener.class',
                'shadow/junit/framework/TestResult$1.class',
                'shadow/junit/framework/TestResult.class',
                'shadow/junit/framework/TestSuite$1.class',
                'shadow/junit/framework/TestSuite.class'
        ])
    }
}
