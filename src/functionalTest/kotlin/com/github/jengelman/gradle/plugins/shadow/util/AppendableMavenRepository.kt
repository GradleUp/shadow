package com.github.jengelman.gradle.plugins.shadow.util

import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import org.apache.maven.model.Dependency
import org.apache.maven.model.DependencyManagement
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.gradle.api.logging.Logging

class AppendableMavenRepository(val root: Path) {
  private val modules = mutableListOf<Module>()
  private val jarsDir: Path

  init {
    check(root.exists()) { "Maven repository root directory does not exist: $root" }
    jarsDir = root.resolve("jars").createDirectory()
  }

  fun jarModule(
    groupId: String,
    artifactId: String,
    version: String,
    action: JarModule.() -> Unit,
  ): String {
    val jarModule = JarModule(groupId, artifactId, version).also(action)
    modules += jarModule
    return jarModule.coordinate
  }

  fun bomModule(
    groupId: String,
    artifactId: String,
    version: String,
    action: BomModule.() -> Unit,
  ): String {
    val bomModule = BomModule(groupId, artifactId, version).also(action)
    modules += bomModule
    return bomModule.coordinate
  }

  fun publish() {
    check(modules.isNotEmpty()) { "No modules to publish. Please add at least one module." }
    val writer = MavenXpp3Writer()

    modules.forEach { module ->
      val groupPath = module.groupId.replace('.', '/')
      val versionDir =
        root.resolve("$groupPath/${module.artifactId}/${module.version}").createDirectories()
      val pomFile = versionDir.resolve("${module.artifactId}-${module.version}.pom")

      val model =
        Model().apply {
          modelVersion = "4.0.0"
          groupId = module.groupId
          artifactId = module.artifactId
          version = module.version
          when (module) {
            is JarModule -> {
              dependencies = module.dependencies
            }
            is BomModule -> {
              packaging = "pom"
              dependencyManagement =
                DependencyManagement().apply { dependencies = module.dependencies }
            }
          }
        }

      pomFile.bufferedWriter().use { writer.write(it, model) }

      if (module is JarModule) {
        val jar = module.existingJar ?: error("No jar file provided for ${module.coordinate}")
        val targetJar = versionDir.resolve("${module.artifactId}-${module.version}.jar")
        jar.copyTo(targetJar, overwrite = true)

        module.existingSourcesJar?.let { sourcesJar ->
          val targetSourcesJar =
            versionDir.resolve("${module.artifactId}-${module.version}-sources.jar")
          sourcesJar.copyTo(targetSourcesJar, overwrite = true)
        }
      }
    }

    logger.info(
      """
      |Publish modules to Maven repository at ${root.toUri()}:
      |${modules.joinToString("\n") { it.coordinate }}
      """
        .trimMargin()
    )
    modules.clear()
  }

  sealed class Module(val groupId: String, val artifactId: String, val version: String) {
    val coordinate = "$groupId:$artifactId:$version"
    val dependencies = mutableListOf<Dependency>()

    fun addDependency(coordinate: String, scope: String = "runtime") {
      val (groupId, artifactId, version) =
        coordinate.split(":").takeIf { it.size == 3 }
          ?: error(
            "Invalid coordinate format: '$coordinate'. Expected format is 'groupId:artifactId:version'."
          )
      val dependency =
        Dependency().also {
          it.groupId = groupId
          it.artifactId = artifactId
          it.version = version
          it.scope = scope
        }
      dependencies += dependency
    }
  }

  inner class JarModule(groupId: String, artifactId: String, version: String) :
    Module(groupId, artifactId, version) {
    var existingJar: Path? = null
      private set

    var existingSourcesJar: Path? = null
      private set

    fun useJar(existingJar: Path) {
      this.existingJar = existingJar
    }

    fun buildJar(builder: JarBuilder.() -> Unit) {
      val jarPath = jarsDir.resolve("${coordinate.replace(':', '-')}.jar")
      existingJar = JarBuilder(jarPath).apply(builder).write()
    }

    fun buildSourcesJar(builder: JarBuilder.() -> Unit) {
      val jarPath = jarsDir.resolve("${coordinate.replace(':', '-')}-sources.jar")
      existingSourcesJar = JarBuilder(jarPath).apply(builder).write()
    }
  }

  class BomModule(groupId: String, artifactId: String, version: String) :
    Module(groupId, artifactId, version)
}

private val logger = Logging.getLogger(AppendableMavenRepository::class.java)

val Dependency.coordinate: String
  get() = "$groupId:$artifactId:$version"
