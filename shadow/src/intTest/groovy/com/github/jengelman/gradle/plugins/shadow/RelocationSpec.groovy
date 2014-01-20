package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.support.ShadowPluginIntegrationSpec

class RelocationSpec extends ShadowPluginIntegrationSpec {

    def "relocate files"() {
        given:
        buildFile << '''
repositories {
    mavenCentral()
}

dependencies {
    compile 'junit:junit:3.8.2'
}

shadow {
    artifactAttached = false
    relocation {
        pattern = 'junit.textui'
        shadedPattern = 'a'
    }
    relocation {
        pattern = 'junit.framework'
        shadedPattern = 'b'
    }
}
'''

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        contains([
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
        doesNotContain([
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

    def "relocate files with includes and excludes"() {
        given:
        buildFile << '''
repositories {
    mavenCentral()
}

dependencies {
    compile 'junit:junit:3.8.2'
}

shadow {
    artifactAttached = false
    relocation {
        pattern = 'junit.textui'
        shadedPattern = 'a'
        excludes = ['junit.textui.TestRunner']
    }
    relocation {
        pattern = 'junit.framework'
        shadedPattern = 'b'
        includes = ['junit.framework.Test*']
    }
}
'''

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        contains([
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
        doesNotContain([
                'a/TestRunner.class',
                'b/Assert.class',
                'b/AssertionFailedError.class',
                'b/ComparisonCompactor.class',
                'b/ComparisonFailure.class',
                'b/Protectable.class'
        ])
    }
}