package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.file.FileCollection
import org.vafer.jdependency.Clazz
import org.vafer.jdependency.Clazzpath
import org.vafer.jdependency.ClazzpathUnit

/**
 * An implementation of an {@code UnusedTracker} that uses jdependency to analyse whether
 * a class is actually being used. This is achieved by loading each individual class and
 * collect its references to other classes. Any class that is not referenced at all from
 * a project unit, i.e. a class that should be kept, can be safely removed.
 * <p>
 * This approach is effective, however it fails to shrink all unused classes as it operates
 * only on class level rather than on each used method.
 */
class UnusedTrackerUsingJDependency extends UnusedTracker {
    private final List<ClazzpathUnit> projectUnits
    private final Clazzpath cp = new Clazzpath()

    private UnusedTrackerUsingJDependency(Iterable<File> classDirs, FileCollection classJars, FileCollection toMinimize) {
        super(toMinimize)
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

    static UnusedTrackerUsingJDependency forProject(FileCollection apiJars, Iterable<File> sourceSetsClassesDirs, FileCollection toMinimize) {
        return new UnusedTrackerUsingJDependency(sourceSetsClassesDirs, apiJars, toMinimize)
    }
}
