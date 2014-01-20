package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class ShadowPluginSpec extends Specification {

    Project project

    def setup() {
        project = new ProjectBuilder().build()
    }

    def "creates extension and shadow task"() {
        when:
        project.plugins.apply(ShadowPlugin)

        then:
        assert project.plugins.hasPlugin(ShadowPlugin)
        assert project.extensions.findByName('shadow')
        assert project.tasks.findByName('shadowJar')
    }
}
