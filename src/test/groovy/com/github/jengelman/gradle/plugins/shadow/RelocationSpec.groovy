package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import spock.lang.Issue

import java.util.jar.Attributes
import java.util.jar.JarFile

class RelocationSpec extends PluginSpecification {

    @Issue('SHADOW-58')
    def "relocate dependency files"() {
        given:
        buildFile << """
            dependencies {
               compile 'junit:junit:3.8.2'
            }
            
            shadowJar {
               relocate 'junit.textui', 'a'
               relocate 'junit.framework', 'b'
               manifest {
                   attributes 'TEST-VALUE': 'FOO'
               }
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

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
               compile 'junit:junit:3.8.2'
            }
            
            // tag::relocateFilter[]
            shadowJar {
               relocate('junit.textui', 'a') {
                   exclude 'junit.textui.TestRunner'
               }
               relocate('junit.framework', 'b') {
                   include 'junit.framework.Test*'
               }
            }
            // end::relocateFilter[]
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

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

    @Issue(['SHADOW-55', 'SHADOW-53'])
    def "remap class names for relocated files in project source"() {
        given:
        buildFile << """
            dependencies {
               compile 'junit:junit:3.8.2'
            }
            
            // tag::relocate[]
            shadowJar {
               relocate 'junit.framework', 'shadow.junit'
            }
            // end::relocate[]
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
        runner.withArguments('shadowJar').build()

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

    @Issue('SHADOW-61')
    def "relocate does not drop dependency resources"() {
        given: 'Core project with dependency and resource'
        file('core/build.gradle') << """
        apply plugin: 'java'
        
        repositories { maven { url "${repo.uri}" } }
        dependencies { compile 'junit:junit:3.8.2' }
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
        apply plugin: 'java'
        apply plugin: 'com.github.johnrengelman.shadow'
        
        repositories { maven { url "${repo.uri}" } }
        dependencies { compile project(':core') }
        
        shadowJar {
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
        runner.withArguments(':app:shadowJar').build()

        then:
        File appOutput = file('app/build/libs/app-all.jar')
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

    @Issue(['SHADOW-93', 'SHADOW-114'])
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
               compile 'shadow:dep:1.0'
            }
            
            shadowJar {
               relocate 'foo', 'bar'
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

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

    @Issue("SHADOW-294")
    def "does not error on relocating java9 classes"() {
        given:
        buildFile << """
            repositories {
                jcenter()
                maven {
                    url 'http://repository.mapr.com/nexus/content/groups/mapr-public'
                }
            }

            dependencies {
                compile 'org.slf4j:slf4j-api:1.7.21'
                compile group: 'io.netty', name: 'netty-all', version: '4.0.23.Final'
                compile group: 'com.google.protobuf', name: 'protobuf-java', version: '2.5.0'
                compile group: 'org.apache.zookeeper', name: 'zookeeper', version: '3.4.6'
                compile group: 'org.hbase', name: 'asynchbase', version: '1.7.0-mapr-1603'
            }

            shadowJar {
                zip64 true
                relocate 'com.google.protobuf', 'shaded.com.google.protobuf'
                relocate 'io.netty', 'shaded.io.netty'
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar').build()

        then:
        noExceptionThrown()

    }
}
