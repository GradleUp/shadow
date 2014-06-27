package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.functional.ExecutionResult
import spock.lang.Issue

class RelocationSpec extends PluginSpecification {

    def "relocate dependency files"() {
        given:
        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { jcenter() }
            |
            |dependencies {
            |   compile 'junit:junit:3.8.2'
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |   relocate 'junit.textui', 'a'
            |   relocate 'junit.framework', 'b'
            |}
        """.stripMargin()

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
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { jcenter() }
            |
            |dependencies {
            |   compile 'junit:junit:3.8.2'
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |   relocate('junit.textui', 'a') {
            |       exclude 'junit.textui.TestRunner'
            |   }
            |   relocate('junit.framework', 'b') {
            |       include 'junit.framework.Test*'
            |   }
            |}
        """.stripMargin()

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

    @Issue(['SHADOW-55', 'SHADOW-53'])
    def "remap class names for relocated files in project source"() {
        given:
        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { jcenter() }
            |
            |dependencies {
            |   compile 'junit:junit:3.8.2'
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |   relocate 'junit.framework', 'shadow.junit'
            |}
        """.stripMargin()

        file('src/main/java/shadow/ShadowTest.java') << '''
            |package shadow;
            |
            |import junit.framework.Test;
            |import junit.framework.TestResult;
            |public class ShadowTest implements Test {
            |  public int countTestCases() { return 0; }
            |  public void run(TestResult result) { }
            |}
        '''.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)

        and:
        contains(output, [
                'shadow/ShadowTest.class',
                'shadow/junit/Test.class',
                'shadow/junit'
        ])

        and:
        doesNotContain(output, [
                'junit/framework',
                'junit/framework/Test.class'
        ])

        and: 'check that the class can be loaded. If the file was not relocated properly, we should get a NoDefClassFound'
        // Isolated class loader with only the JVM system jars and the output jar from the test project
        URLClassLoader classLoader = new URLClassLoader([output.toURI().toURL()] as URL[],
                ClassLoader.systemClassLoader.parent)
        classLoader.loadClass('shadow.ShadowTest')
    }
}
