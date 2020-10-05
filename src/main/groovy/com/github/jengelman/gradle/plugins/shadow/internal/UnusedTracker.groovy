package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.vafer.jdependency.Clazz
import org.vafer.jdependency.Clazzpath
import org.vafer.jdependency.ClazzpathUnit

/** Tracks unused classes in the project classpath. */
class UnusedTracker {
    private final FileCollection toMinimize
    private final List<ClazzpathUnit> projectUnits
    private final Clazzpath cp = new Clazzpath()

    private UnusedTracker(Iterable<File> classDirs, FileCollection classJars, FileCollection toMinimize) {
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

    static UnusedTracker forProject(FileCollection apiJars, Iterable<File> sourceSetsClassesDirs, FileCollection toMinimize) {
        return new UnusedTracker(sourceSetsClassesDirs, apiJars, toMinimize)
    }

    @InputFiles
    FileCollection getToMinimize() {
        return toMinimize
    }

    private static boolean isProjectDependencyFile(File file, Dependency dep) {
        def fileName = file.name
        def dependencyName = dep.name

        return (fileName == "${dependencyName}.jar") ||
                (fileName.startsWith("${dependencyName}-") && fileName.endsWith('.jar'))
    }

    private static void addJar(Configuration config, Dependency dep, List<File> result) {
        def file = config.find { isProjectDependencyFile(it, dep) } as File
        if (file != null) {
            result.add(file)
        }
    }

    static FileCollection getApiJarsFromProject(Project project) {
        def apiDependencies = project.configurations.asMap['api']?.dependencies ?: null
        if (apiDependencies == null) return project.files()

        def runtimeConfiguration = project.configurations.asMap['runtimeClasspath'] ?: project.configurations.runtime
        def apiJars = new LinkedList<File>()
        apiDependencies.each { dep ->
            if (dep instanceof ProjectDependency) {
                apiJars.addAll(getApiJarsFromProject(dep.dependencyProject))
                addJar(runtimeConfiguration, dep, apiJars)
            } else if (dep instanceof SelfResolvingDependency) {
                apiJars.addAll(dep.resolve())
            } else {
                addJar(runtimeConfiguration, dep, apiJars)
                apiJars.add(runtimeConfiguration.find { it.name.startsWith("${dep.name}-") } as File)
            }
        }

        return project.files(apiJars)
    }
}
