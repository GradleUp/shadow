package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
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

    static FileCollection getApiJarsFromProject(Project project) {
        def apiConfiguration = project.configurations.findByName("api")
        if (apiConfiguration == null) return project.files()

        def configName = "shadowMinimizeApi"
        def shadowApiConfig
        if (project.configurations.names.contains(configName)) {
            shadowApiConfig = project.configurations.named(configName)
        } else {
            shadowApiConfig = project.configurations.register(configName) { config ->
                config.canBeResolved = true
                config.canBeConsumed = false
                config.attributes { attrs ->
                    attrs.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_API))
                    attrs.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
                    attrs.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                }
                config.extendsFrom(apiConfiguration)
            }
        }

        return project.files(shadowApiConfig.flatMap { shadowApi ->
            shadowApi.incoming.artifacts.resolvedArtifacts.map { artifacts ->
                artifacts
                    .findAll { it.id.componentIdentifier !instanceof ModuleComponentIdentifier }
                    .collect { it.file }
            }
        })
    }
}
