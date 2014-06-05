package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.functional.ExecutionResult

class RelocationSpec extends PluginSpecification {

    def "relocate dependency files"() {
        given:
        buildFile << """
apply plugin: ${ShadowPlugin.name}

repositories { jcenter() }

dependencies {
    compile 'junit:junit:3.8.2'
}

shadowJar {
    baseName = 'shadow'
    classifier = null
    relocate 'junit.textui', 'a'
    relocate 'junit.framework', 'b'
}
"""
        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, [
                'a/ResultPrinter.class',
                'a/TestRunner.class',
                'b/Assert.class',
                'b/AssertionFailedError.class',
                'b/ComparisonCompactor.class',
                'b/ComparisonFailure.class',
                'b/Protectable.class',
                'b/Test.class',
                'b/TestCase.class',
                'b/TestFailure.class',
                'b/TestListener.class',
                'b/TestResult$1.class',
                'b/TestResult.class',
                'b/TestSuite$1.class',
                'b/TestSuite.class'
        ])

        and:
        doesNotContain(output, [
                'junit/textui/ResultPrinter.class',
                'junit/textui/TestRunner.class',
                'junit/framework/Assert.class',
                'junit/framework/AssertionFailedError.class',
                'junit/framework/ComparisonCompactor.class',
                'junit/framework/ComparisonFailure.class',
                'junit/framework/Protectable.class',
                'junit/framework/Test.class',
                'junit/framework/TestCase.class',
                'junit/framework/TestFailure.class',
                'junit/framework/TestListener.class',
                'junit/framework/TestResult$1.class',
                'junit/framework/TestResult.class',
                'junit/framework/TestSuite$1.class',
                'junit/framework/TestSuite.class'
        ])
    }

    def "relocate dependency files with filtering"() {
        given:
        buildFile << """
apply plugin: ${ShadowPlugin.name}

repositories { jcenter() }

dependencies {
    compile 'junit:junit:3.8.2'
}

shadowJar {
    baseName = 'shadow'
    classifier = null
    relocate('junit.textui', 'a') {
        exclude 'junit.textui.TestRunner'
    }
    relocate('junit.framework', 'b') {
        include 'junit.framework.Test*'
    }
}
"""
        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, [
                'a/ResultPrinter.class',
                'b/Test.class',
                'b/TestCase.class',
                'b/TestFailure.class',
                'b/TestListener.class',
                'b/TestResult$1.class',
                'b/TestResult.class',
                'b/TestSuite$1.class',
                'b/TestSuite.class'
        ])

        and:
        doesNotContain(output, [
                'a/TestRunner.class',
                'b/Assert.class',
                'b/AssertionFailedError.class',
                'b/ComparisonCompactor.class',
                'b/ComparisonFailure.class',
                'b/Protectable.class'
        ])

        and:
        contains(output, [
                'junit/textui/TestRunner.class',
                'junit/framework/Assert.class',
                'junit/framework/AssertionFailedError.class',
                'junit/framework/ComparisonCompactor.class',
                'junit/framework/ComparisonFailure.class',
                'junit/framework/Protectable.class'
        ])
    }
}
