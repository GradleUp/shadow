package com.github.jengelman.gradle.plugins.shadow2

import com.github.jengelman.gradle.plugins.shadow.Shadow2Plugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow2.util.PluginSpecification
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.functional.ExecutionResult

class Shadow2PluginSpec extends PluginSpecification {

    def 'apply plugin'() {
        given:
        Project project = ProjectBuilder.builder().build()

        when:
        project.plugins.apply(Shadow2Plugin)

        then:
        project.plugins.hasPlugin(Shadow2Plugin)
    }

    def 'shadow copy'() {
        given:
        URL artifact = this.class.classLoader.getResource('test-artifact-1.0-SNAPSHOT.jar')
        URL project = this.class.classLoader.getResource('test-project-1.0-SNAPSHOT.jar')

        buildFile << """
            task shadow(type: ${ShadowJar.name}) {
                destinationDir = buildDir
                baseName = 'shadow'
                from('${artifact.path}')
                from('${project.path}')
            }
        """

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File output = file('build/shadow.jar')
        assert output.exists()
    }
}
