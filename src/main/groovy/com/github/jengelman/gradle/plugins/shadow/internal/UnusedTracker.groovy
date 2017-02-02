package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.vafer.jdependency.Clazz
import org.vafer.jdependency.Clazzpath
import org.vafer.jdependency.ClazzpathUnit

/** Tracks unused classes in the project classpath. */
class UnusedTracker {
    private final List<String> entryPoints
    private final List<ClazzpathUnit> projectUnits
    private final Clazzpath cp = new Clazzpath()

    private UnusedTracker(List<File> classDirs, List<String> entryPoints) {
        this.entryPoints = entryPoints
        projectUnits = classDirs.collect { cp.addClazzpathUnit(it) }
    }

    Set<String> findUnused() {
        Set<Clazz> unused = cp.clazzes

        for (cpu in projectUnits) {
            unused.removeAll(cpu.clazzes)
            unused.removeAll(cpu.transitiveDependencies)
        }

        for (entryPoint in entryPoints) {
            Clazz clazz = cp.getClazz(entryPoint)
            if (clazz == null) {
                throw new RuntimeException("Entry point not found: " + className);
            }

            unused.remove(clazz)
            unused.removeAll(clazz.transitiveDependencies)
        }

        return unused.collect { it.name }.toSet()
    }

    void addDependency(File jarOrDir) {
        cp.addClazzpathUnit(jarOrDir)
    }

    static UnusedTracker forProject(Project project, List<String> entryPoints) {
        final List<File> classDirs = new ArrayList<>()
        for (SourceSet sourceSet in project.sourceSets) {
            File classDir = sourceSet.output.classesDir
            if (classDir.isDirectory()) {
                classDirs.add(classDir)
            }
        }

        new UnusedTracker(classDirs, entryPoints)
    }
}
