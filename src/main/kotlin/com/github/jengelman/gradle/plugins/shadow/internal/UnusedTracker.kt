package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import java.io.IOException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME
import org.gradle.api.provider.Provider
import org.vafer.jdependency.Clazz
import org.vafer.jdependency.Clazzpath

internal fun Project.getApiJars(): Provider<List<File>> {
  val apiConfiguration =
    configurations.findByName(API_CONFIGURATION_NAME) ?: return provider { emptyList() }

  val configName = "shadowMinimizeApi"
  val shadowApiConfig =
    if (configurations.names.contains(configName)) {
      configurations.named(configName)
    } else {
      configurations.resolvable(configName) {
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

/**
 * Finds unused classes in the project classpath.
 *
 * Modified from
 * [org.apache.maven.plugins.shade.filter.MinijarFilter.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/filter/MinijarFilter.java).
 *
 * Related to MSHADE-313.
 */
internal fun findUnusedClasses(
  sourceSetsClassesDirs: Iterable<File>,
  classJars: Iterable<File>,
  toMinimize: Iterable<File>,
  dependencies: Iterable<File>,
  resourcesDirs: Iterable<File>,
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

  removeServices(cp, unused, dependencies + sourceSetsClassesDirs + resourcesDirs)

  return unused.map { it.name }.toSet()
}

private const val SERVICES_PATH = "META-INF/services/"

private fun removeServices(
  cp: Clazzpath,
  unused: MutableSet<Clazz>,
  files: Iterable<File>,
) {
  val services = mutableMapOf<String, MutableSet<String>>()
  for (file in files) {
    collectServices(file, services)
  }

  if (services.isEmpty()) return

  do {
    var repeatScan = false
    for ((serviceName, providers) in services) {
      val serviceClazz = cp.getClazz(serviceName) ?: continue
      if (serviceClazz !in unused) {
        for (providerName in providers) {
          val providerClazz = cp.getClazz(providerName) ?: continue
          if (providerClazz in unused) {
            unused.remove(providerClazz)
            unused.removeAll(providerClazz.transitiveDependencies)
            repeatScan = true
          }
        }
      }
    }
  } while (repeatScan)
}

private fun collectServices(file: File, services: MutableMap<String, MutableSet<String>>) {
  when {
    file.isDirectory -> {
      val servicesDir = file.resolve(SERVICES_PATH)
      if (servicesDir.isDirectory) {
        servicesDir
          .listFiles()
          ?.filter { it.isFile }
          ?.forEach { serviceFile ->
            val serviceName = serviceFile.name
            if (serviceName.isNotEmpty() && !serviceName.contains('/')) {
              try {
                serviceFile.useLines { lines ->
                  lines.forEach { line ->
                    val provider = line.substringBefore('#').trim()
                    if (provider.isNotEmpty()) {
                      services.getOrPut(serviceName) { mutableSetOf() }.add(provider)
                    }
                  }
                }
              } catch (_: IOException) {
                // Ignore unreadable files.
              }
            }
          }
      }
    }
    file.isFile -> {
      try {
        file.useZip {
          entries().asSequence().forEach { entry ->
            if (!entry.isDirectory && entry.name.startsWith(SERVICES_PATH)) {
              val serviceName = entry.name.removePrefix(SERVICES_PATH)
              if (serviceName.isNotEmpty() && !serviceName.contains('/')) {
                getInputStream(entry).bufferedReader().useLines { lines ->
                  lines.forEach { line ->
                    val provider = line.substringBefore('#').trim()
                    if (provider.isNotEmpty()) {
                      services.getOrPut(serviceName) { mutableSetOf() }.add(provider)
                    }
                  }
                }
              }
            }
          }
        }
      } catch (_: Exception) {
        // Ignore unreadable or non-zip files.
      }
    }
  }
}
