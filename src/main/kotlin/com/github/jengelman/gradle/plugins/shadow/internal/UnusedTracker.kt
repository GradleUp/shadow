package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.vafer.jdependency.Clazzpath
import org.vafer.jdependency.ClazzpathUnit

/** Tracks unused classes in the project classpath. */
internal class UnusedTracker(
  sourceSetsClassesDirs: Iterable<File>,
  classJars: FileCollection,
  @get:InputFiles val toMinimize: FileCollection,
) {
  private val projectUnits: List<ClazzpathUnit>
  private val cp = Clazzpath()

  init {
    projectUnits = sourceSetsClassesDirs.map { cp.addClazzpathUnit(it) } + classJars.map { cp.addClazzpathUnit(it) }
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
    fun getApiJarsFromProject(project: Project): FileCollection {
      val apiDependencies = project.configurations.findByName("api")?.dependencies
        ?: return project.files()
      val runtimeConfiguration = project.runtimeConfiguration
      val apiJars = mutableListOf<File>()
      apiDependencies.forEach { dep ->
        when (dep) {
          is ProjectDependency -> {
            apiJars.addAll(getApiJarsFromProject(project.project(dep.path)))
            addJar(runtimeConfiguration, dep, apiJars)
          }
          is FileCollectionDependency -> {
            apiJars.addAll(dep.files)
          }
          // Skip BOM dependencies and other non-JAR dependencies.
          is ExternalModuleDependency -> Unit
          else -> {
            addJar(runtimeConfiguration, dep, apiJars)
            val jarFile = runtimeConfiguration.find { it.name.startsWith("${dep.name}-") } ?: return@forEach
            apiJars.add(jarFile)
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
