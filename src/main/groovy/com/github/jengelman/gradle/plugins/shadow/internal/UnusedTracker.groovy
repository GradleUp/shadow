package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet
import org.vafer.jdependency.Clazz
import org.vafer.jdependency.Clazzpath
import org.vafer.jdependency.ClazzpathUnit

/** Tracks unused classes in the project classpath. */
class UnusedTracker {
    private final FileCollection toMinimize
    private final List<ClazzpathUnit> projectUnits
    private final Clazzpath cp = new Clazzpath()

    private UnusedTracker(List<File> classDirs, FileCollection classJars, FileCollection toMinimize) {
        this.toMinimize = toMinimize
        projectUnits = classDirs.collect { cp.addClazzpathUnit(it) }
        projectUnits.addAll(classJars.collect { cp.addClazzpathUnit(it) })
    }

    Set<String> findUnused() {
        Set<Clazz> unused = cp.clazzes
        for (cpu in projectUnits) {
            unused.removeAll(cpu.clazzes)
            unused.removeAll(cpu.transitiveDependencies)
        }
        return unused.collect { it.name }.toSet()
    }

    void addDependency(File jarOrDir) {
        if (toMinimize.contains(jarOrDir)) {
            cp.addClazzpathUnit(jarOrDir)
        }
    }

    static UnusedTracker forProject(Project project, List<Configuration> configurations, DependencyFilter dependencyFilter) {
        def apiJars = getApiJarsFromProject(project)
        FileCollection toMinimize = dependencyFilter.resolve(configurations) - apiJars

        final List<File> classDirs = new ArrayList<>()
        for (SourceSet sourceSet in project.sourceSets) {
            Iterable<File> classesDirs = sourceSet.output.classesDirs
            classDirs.addAll(classesDirs.findAll { it.isDirectory() })
        }
        return new UnusedTracker(classDirs, apiJars, toMinimize)
    }

    private static FileCollection getApiJarsFromProject(Project project) {
        def apiDependencies = project.configurations.asMap['api']?.dependencies ?: null
        if (apiDependencies == null) return project.files()

        def runtimeConfiguration = project.configurations.asMap['runtimeClasspath'] ?: project.configurations.runtime
        def apiJars = new LinkedList<File>()
        apiDependencies.each { dep ->
            if (dep instanceof ProjectDependency) {
                apiJars.addAll(getApiJarsFromProject(dep.dependencyProject))
                def jar = runtimeConfiguration.find { it.name.contains("${dep.name}") && it.name.endsWith(".jar") }
                if (jar != null) apiJars.add(jar as File)
            } else if (dep instanceof SelfResolvingDependency) {
                apiJars.addAll(dep.resolve())
            } else {
                def jar = runtimeConfiguration.find { it.name.startsWith("${dep.name}-") }
                if (jar != null) apiJars.add(jar as File)
            }
        }

        return project.files(apiJars)
    }
}
