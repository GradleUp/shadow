package com.github.jengelman.gradle.plugins.shadow

class ConfigureShadowRelocationSpec extends BasePluginSpecification {

    def "auto relocate plugin dependencies"() {
        given:
        buildFile << """
            tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
                enableRelocation = true
            }

            dependencies {
               implementation 'junit:junit:3.8.2'
            }
        """.stripIndent()

        when:
        run('shadowJar', '-s')

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
