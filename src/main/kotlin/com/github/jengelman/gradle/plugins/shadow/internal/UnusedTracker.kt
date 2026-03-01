package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME
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
    projectUnits =
      sourceSetsClassesDirs.map { cp.addClazzpathUnit(it) } +
        classJars.map { cp.addClazzpathUnit(it) }
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
      val apiConfiguration =
        project.configurations.findByName(API_CONFIGURATION_NAME) ?: return project.files()

      val shadowApiConfig =
        project.configurations.register(
          @OptIn(ExperimentalUuidApi::class)
          "shadowMinimizeApi_${Uuid.random().toString().substring(0, 8)}"
        ) {
          it.isCanBeResolved = true
          it.isCanBeConsumed = false
          it.attributes { attrs ->
            attrs.attribute(
              Usage.USAGE_ATTRIBUTE,
              project.objects.named(Usage::class.java, Usage.JAVA_API),
            )
            attrs.attribute(
              Category.CATEGORY_ATTRIBUTE,
              project.objects.named(Category::class.java, Category.LIBRARY),
            )
            attrs.attribute(
              LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
              project.objects.named(LibraryElements::class.java, LibraryElements.JAR),
            )
          }
          it.extendsFrom(apiConfiguration)
        }

      return project.files(
        shadowApiConfig.map { shadowApi ->
          shadowApi.resolvedConfiguration.resolvedArtifacts
            .filter { artifact -> artifact.id.componentIdentifier !is ModuleComponentIdentifier }
            .map { it.file }
        }
      )
    }
  }
}
