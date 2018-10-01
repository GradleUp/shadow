package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
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
        def apiLibs = project.files(getApiLibs(project))
        FileCollection toMinimize = dependencyFilter.resolve(configurations) - apiLibs

        final List<File> classDirs = new ArrayList<>()
        for (SourceSet sourceSet in project.sourceSets) {
            Iterable<File> classesDirs = sourceSet.output.classesDirs
            classDirs.addAll(classesDirs.findAll { it.isDirectory() })
        }
        return new UnusedTracker(classDirs, apiLibs, toMinimize)
    }

    private static List<File> getApiLibs(Project project) {
        def apiDependencies = project.configurations.asMap['api']?.dependencies ?: null
        if (apiDependencies == null) return Collections.emptyList()

        def runtimeConfiguration = project.configurations.asMap['runtimeClasspath'] ?: project.configurations.runtime
        def apiLibs = new LinkedList<File>()
        apiDependencies.each { dep ->
            if (dep instanceof ProjectDependency) {
                apiLibs.addAll(getApiLibs(dep.dependencyProject))
            }

            apiLibs.add(runtimeConfiguration.find { it.name.startsWith(dep.name) } as File)
        }

        return apiLibs
    }
}
