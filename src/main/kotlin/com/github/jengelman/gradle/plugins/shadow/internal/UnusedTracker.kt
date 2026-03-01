package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
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
    fun getApiJarsFromProject(project: Project, apiConfig: Configuration): FileCollection {
      val extension =
        project.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
      if (extension == null) return project.files()

      val shadowApiConfig =
        project.configurations.create(
          "shadowMinimizeApi_${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        ) {
          it.isCanBeResolved = true
          it.isCanBeConsumed = false
          it.attributes { attrs ->
            attrs.attribute(
              org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE,
              project.objects.named(
                org.gradle.api.attributes.Usage::class.java,
                org.gradle.api.attributes.Usage.JAVA_API,
              ),
            )
            attrs.attribute(
              org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE,
              project.objects.named(
                org.gradle.api.attributes.Category::class.java,
                org.gradle.api.attributes.Category.LIBRARY,
              ),
            )
            attrs.attribute(
              org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
              project.objects.named(
                org.gradle.api.attributes.LibraryElements::class.java,
                org.gradle.api.attributes.LibraryElements.JAR,
              ),
            )
          }
          it.extendsFrom(apiConfig)
        }

      return project.files(
        project.provider {
          shadowApiConfig.resolvedConfiguration.resolvedArtifacts
            .filter { artifact ->
              artifact.id.componentIdentifier !is
                org.gradle.api.artifacts.component.ModuleComponentIdentifier
            }
            .map { it.file }
        }
      )
    }
  }
}
