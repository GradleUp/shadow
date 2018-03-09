package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Paths

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

    /**
     * In conjunction with the 'maven-publish' plugin, 'java-gradle-plugin' automatically creates some MavenPublications.
     * <p>
     * This test serves as an example of how to use {@link PluginShadowPlugin} without explicitly setting
     * {@link org.gradle.plugin.devel.GradlePluginDevelopmentExtension#setAutomatedPublishing(boolean)} to false.
     * <p>
     * Note this also requires a small change in
     * {@link ShadowExtension#component(org.gradle.api.publish.maven.MavenPublication)} to prevent it from adding a
     * second {@literal `<dependencies>`} element when one was already created by the application of 'java-gradle-plugin'.
     */
    def "play nice with 'maven-publish' and 'java-gradle-plugin'"() {
        given:
        buildFile.text = buildFile.text.replace('com.github.johnrengelman.shadow', 'com.github.johnrengelman.plugin-shadow')
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-gradle-plugin'
            
            dependencies {
                shadow 'junit:junit:3.8.2'
            }
            
            gradlePlugin {
                plugins {
                    fake {
                        id = 'com.acme.fake'
                        implementationClass = 'com.acme.Fake'
                    }
                }
            }
            
            // Remove the gradleApi so it isn't merged into the jar file.
            configurations.compile.dependencies.remove dependencies.gradleApi()
            
            shadowJar {
                classifier = ''
            }
                        
            publishing {
                publications {
                    MavenPublication myPub = maybeCreate('pluginMaven', MavenPublication)
                    myPub.artifacts.clear() //TODO doesn't have desired effect
                    project.shadow.component(myPub)
                }
            }
        """.stripIndent()

        when:
        BuildResult result = runner.withArguments('publishToMavenLocal', '-s')
                .build() //FIXME fails with InvalidMavenPublicationException @ ValidatingMavenPublisher#checkNoDuplicateArtifacts

        then:
        result.task(':generatePomFileForPluginMavenPublication').outcome == TaskOutcome.SUCCESS
        result.task(':generatePomFileForFakePluginMarkerMavenPublication').outcome == TaskOutcome.SUCCESS
        result.task(':shadowJar').outcome == TaskOutcome.SUCCESS
        result.task(':publishToMavenLocal').outcome == TaskOutcome.SUCCESS
        File fatJarPomFile = Paths.get(dir.root.absolutePath, 'build', 'publications', 'pluginMaven', 'pom-default.xml').toFile()
        File fakePluginMarker = Paths.get(dir.root.absolutePath, 'build', 'publications', 'fakePluginMarkerMaven', 'pom-default.xml').toFile()
        File fatJarWithoutClassifier = output('shadow-1.0.jar')
        fatJarPomFile.exists()
        fakePluginMarker.exists()
        fatJarWithoutClassifier.exists()
    }

}
