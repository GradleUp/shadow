package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.vafer.jdependency.Clazzpath
import org.vafer.jdependency.ClazzpathUnit

/** Tracks unused classes in the project classpath. */
internal class UnusedTracker private constructor(
  classDirs: Iterable<File>,
  classJars: FileCollection,
  @get:InputFiles val toMinimize: FileCollection,
) {
  private val projectUnits: List<ClazzpathUnit>
  private val cp = Clazzpath()

  init {
    projectUnits = classDirs.map { cp.addClazzpathUnit(it) } + classJars.map { cp.addClazzpathUnit(it) }
  }

  fun findUnused(): Set<String> {
    val unused = cp.clazzes.toMutableSet()
    for (cpu in projectUnits) {
      unused.removeAll(cpu.clazzes)
      unused.removeAll(cpu.transitiveDependencies)
    }
    return unused.map { it.name }.toSet()
  }

  fun addDependency(jarOrDir: File) {
    if (toMinimize.contains(jarOrDir)) {
      cp.addClazzpathUnit(jarOrDir)
    }
  }

  companion object {
    fun forProject(
      apiJars: FileCollection,
      sourceSetsClassesDirs: Iterable<File>,
      toMinimize: FileCollection,
    ): UnusedTracker {
      return UnusedTracker(sourceSetsClassesDirs, apiJars, toMinimize)
    }

    fun getApiJarsFromProject(project: Project): FileCollection {
      val apiDependencies = project.configurations.findByName("api")?.dependencies
        ?: return project.files()
      val runtimeConfiguration = project.runtimeConfiguration
      val apiJars = mutableListOf<File>()
      apiDependencies.forEach { dep ->
        when (dep) {
          is ProjectDependency -> {
            apiJars.addAll(getApiJarsFromProject(dep.dependencyProjectCompat(project)))
            addJar(runtimeConfiguration, dep, apiJars)
          }
          is SelfResolvingDependency -> {
            apiJars.addAll(dep.resolve())
          }
          else -> {
            addJar(runtimeConfiguration, dep, apiJars)
            apiJars.add(runtimeConfiguration.find { it.name.startsWith("${dep.name}-") } as File)
          }
        }
      }
      return project.files(apiJars)
    }

    private fun addJar(config: Configuration, dep: Dependency, result: MutableList<File>) {
      config.find { isProjectDependencyFile(it, dep) }?.let { result.add(it) }
    }

    private fun isProjectDependencyFile(file: File, dep: Dependency): Boolean {
      val fileName = file.name
      val dependencyName = dep.name
      return fileName == "$dependencyName.jar" ||
        (fileName.startsWith("$dependencyName-") && fileName.endsWith(".jar"))
    }
  }
}
