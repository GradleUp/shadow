package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.specs.Spec
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class AbstractDependencyFilterSpec extends Specification {

    def "matches dependency whose version contains plus sign"() {
        given:
        Project project = ProjectBuilder.builder().build()
        AbstractDependencyFilter filter = new DefaultDependencyFilter(project)
        Dependency dep = project.dependencies.create("com.example:foo:1.0.0+1")

        ResolvedDependency resolvedDep = Stub(ResolvedDependency) {
            getModuleGroup() >> "com.example"
            getModuleName() >> "foo"
            getModuleVersion() >> "1.0.0+1"
        }

        when:
        Spec<? super ResolvedDependency> spec = filter.dependency(dep)

        then:
        spec.isSatisfiedBy(resolvedDep)
    }
}
