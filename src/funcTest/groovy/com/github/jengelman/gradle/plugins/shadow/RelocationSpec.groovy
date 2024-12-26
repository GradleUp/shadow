package com.github.jengelman.gradle.plugins.shadow

import spock.lang.Issue

import java.util.jar.Attributes
import java.util.jar.JarFile

class RelocationSpec extends BasePluginSpecification {

    def "auto relocate plugin dependencies"() {
        given:
        buildFile << """
            $shadowJar {
                enableRelocation = true
            }

            dependencies {
               implementation 'junit:junit:3.8.2'
            }
        """.stripIndent()

        when:
        run('shadowJar')

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

    @Issue('https://github.com/GradleUp/shadow/issues/58')
    def "relocate dependency files"() {
        given:
        buildFile << """
            dependencies {
               implementation 'junit:junit:3.8.2'
            }

            $shadowJar {
               relocate 'junit.textui', 'a'
               relocate 'junit.framework', 'b'
               manifest {
                   attributes 'TEST-VALUE': 'FOO'
               }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, [
            'META-INF/MANIFEST.MF',
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

        and: 'Test that manifest file exists with contents'
        JarFile jar = new JarFile(output)
        Attributes attributes = jar.manifest.getMainAttributes()
        String val = attributes.getValue('TEST-VALUE')
        assert val == 'FOO'
    }

    def "relocate dependency files with filtering"() {
        given:
        buildFile << """
            dependencies {
               implementation 'junit:junit:3.8.2'
            }

            $shadowJar {
               relocate('junit.textui', 'a') {
                   exclude 'junit.textui.TestRunner'
               }
               relocate('junit.framework', 'b') {
                   include 'junit.framework.Test*'
               }
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
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

    @Issue([
        'https://github.com/GradleUp/shadow/issues/55',
        'https://github.com/GradleUp/shadow/issues/53',
    ])
    def "remap class names for relocated files in project source"() {
        given:
        buildFile << """
            dependencies {
               implementation 'junit:junit:3.8.2'
            }

            $shadowJar {
               relocate 'junit.framework', 'shadow.junit'
            }
        """.stripIndent()

        file('src/main/java/shadow/ShadowTest.java') << '''
            package shadow;

            import junit.framework.Test;
            import junit.framework.TestResult;
            public class ShadowTest implements Test {
              public int countTestCases() { return 0; }
              public void run(TestResult result) { }
            }
        '''.stripIndent()

        when:
        run('shadowJar')

        then:
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

    @Issue('https://github.com/GradleUp/shadow/issues/61')
    def "relocate does not drop dependency resources"() {
        given: 'Core project with dependency and resource'
        file('core/build.gradle') << """
        apply plugin: 'java-library'

        dependencies { api 'junit:junit:3.8.2' }
        """.stripIndent()

        file('core/src/main/resources/TEST') << 'TEST RESOURCE'
        file('core/src/main/resources/test.properties') << 'name=test'
        file('core/src/main/java/core/Core.java') << '''
        package core;

        import junit.framework.Test;

        public class Core {}
        '''.stripIndent()

        and: 'App project with shadow, relocation, and project dependency'
        file('app/build.gradle') << """
        $defaultBuildScript

        dependencies { implementation project(':core') }

        $shadowJar {
          relocate 'core', 'app.core'
          relocate 'junit.framework', 'app.junit.framework'
        }
        """.stripIndent()

        file('app/src/main/resources/APP-TEST') << 'APP TEST RESOURCE'
        file('app/src/main/java/app/App.java') << '''
        package app;

        import core.Core;
        import junit.framework.Test;

        public class App {}
        '''.stripIndent()

        and: 'Configure multi-project build'
        settingsFile << '''
        include 'core', 'app'
        '''.stripIndent()

        when:
        run(':app:shadowJar')

        then:
        File appOutput = getFile('app/build/libs/app-all.jar')
        assert appOutput.exists()

        and:
        contains(appOutput, [
            'TEST',
            'APP-TEST',
            'test.properties',
            'app/core/Core.class',
            'app/App.class',
            'app/junit/framework/Test.class'
        ])
    }

    @Issue([
        'https://github.com/GradleUp/shadow/issues/93',
        'https://github.com/GradleUp/shadow/issues/114',
    ])
    def "relocate resource files"() {
        given:
        repo.module('shadow', 'dep', '1.0')
            .insertFile('foo/dep.properties', 'c')
            .publish()
        file('src/main/java/foo/Foo.java') << '''
        package foo;

        class Foo {}
        '''.stripIndent()
        file('src/main/resources/foo/foo.properties') << 'name=foo'

        buildFile << """
            dependencies {
               implementation 'shadow:dep:1.0'
            }

            $shadowJar {
               relocate 'foo', 'bar'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, [
            'bar/Foo.class',
            'bar/foo.properties',
            'bar/dep.properties'
        ])

        and:
        doesNotContain(output, [
            'foo/Foo.class',
            'foo/foo.properties',
            'foo/dep.properties'
        ])
    }

    @Issue("https://github.com/GradleUp/shadow/issues/294")
    def "does not error on relocating java9 classes"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org.slf4j:slf4j-api:1.7.21'
                implementation group: 'io.netty', name: 'netty-all', version: '4.0.23.Final'
                implementation group: 'com.google.protobuf', name: 'protobuf-java', version: '2.5.0'
                implementation group: 'org.apache.zookeeper', name: 'zookeeper', version: '3.4.6'
            }

            $shadowJar {
                zip64 = true
                relocate 'com.google.protobuf', 'shaded.com.google.protobuf'
                relocate 'io.netty', 'shaded.io.netty'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        noExceptionThrown()
    }
}
