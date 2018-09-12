package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
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

    private UnusedTracker(List<File> classDirs, FileCollection toMinimize) {
        this.toMinimize = toMinimize
        projectUnits = classDirs.collect { cp.addClazzpathUnit(it) }
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

    static UnusedTracker forProject(Project project, FileCollection toMinimize) {
        final List<File> classDirs = new ArrayList<>()
        for (SourceSet sourceSet in project.sourceSets) {
            Iterable<File> classesDirs = sourceSet.output.hasProperty('classesDirs') ? sourceSet.output.classesDirs : [sourceSet.output.classesDir]
            classDirs.addAll(classesDirs.findAll { it.isDirectory() })
        }
        return new UnusedTracker(classDirs, toMinimize)
    }
}
