package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME
import org.gradle.api.provider.Provider
import org.vafer.jdependency.Clazzpath

internal fun Project.getApiJars(): Provider<List<File>> {
  val apiConfiguration =
    configurations.findByName(API_CONFIGURATION_NAME) ?: return provider { emptyList() }

  val configName = "shadowMinimizeApi"
  val shadowApiConfig =
    if (configurations.names.contains(configName)) {
      configurations.named(configName)
    } else {
      configurations.register(configName) {
        it.isCanBeResolved = true
        it.isCanBeConsumed = false
        it.attributes { attrs ->
          attrs.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_API))
          attrs.attribute(
            Category.CATEGORY_ATTRIBUTE,
            objects.named(Category::class.java, Category.LIBRARY),
          )
          attrs.attribute(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            objects.named(LibraryElements::class.java, LibraryElements.JAR),
          )
        }
        it.extendsFrom(apiConfiguration)
      }
    }

  return shadowApiConfig.flatMap { shadowApi ->
    shadowApi.incoming.artifacts.resolvedArtifacts.map { artifacts ->
      artifacts.filter { it.id.componentIdentifier !is ModuleComponentIdentifier }.map { it.file }
    }
  }
}

/** Finds unused classes in the project classpath. */
internal fun findUnusedClasses(
  sourceSetsClassesDirs: Iterable<File>,
  classJars: Iterable<File>,
  toMinimize: Iterable<File>,
  dependencies: Iterable<File>,
): Set<String> {
  val cp = Clazzpath()
  val projectUnits =
    sourceSetsClassesDirs.map { cp.addClazzpathUnit(it) } +
      classJars.map { cp.addClazzpathUnit(it) }

  dependencies.forEach { jarOrDir ->
    if (toMinimize.contains(jarOrDir)) {
      cp.addClazzpathUnit(jarOrDir)
    }
  }

  val unused = cp.clazzes.toMutableSet()
  for (cpu in projectUnits) {
    unused.removeAll(cpu.clazzes)
    unused.removeAll(cpu.transitiveDependencies)
  }
  return unused.map { it.name }.toSet()
}
