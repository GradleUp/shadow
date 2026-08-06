package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.testkit.gradleRunner
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.writeText
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.logging.Logging

class AppendableMavenRepository(val root: Path) {
  private val modules = mutableListOf<Module>()
  private val jarsDir: Path

  init {
    check(root.exists()) { "Maven repository root directory does not exist: $root" }

    root.resolve("settings.gradle").createFile().writeText("rootProject.name = '${root.name}'\n")
    root.resolve("build.gradle").createFile()
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

  private var publishCounter = 0

  fun publish() {
    check(modules.isNotEmpty()) { "No modules to publish. Please add at least one module." }
    modules
      .groupBy { it::class }
      .forEach { (type, group) ->
        @Suppress("UNCHECKED_CAST")
        when (type) {
          JarModule::class -> {
            configureJarModules(group as List<JarModule>)
          }
          BomModule::class -> {
            configureBomModules(group as List<BomModule>)
          }
          else -> error("Unsupported module type: $type")
        }
      }

    gradleRunner(projectDir = root, arguments = listOf("publish", "--stacktrace")).build()

    logger.info(
      """
      |Publish modules to Maven repository at ${root.toUri()}:
      |${modules.joinToString("\n") { it.coordinate }}
      """
        .trimMargin()
    )
    modules.clear()
  }

  private fun configureJarModules(jarModules: List<JarModule>) {
    val mavenPublications =
      jarModules.joinToString("\n") { module ->
        var index = -1
        val nodes =
          module.dependencies.joinToString("\n") {
            index++
            val node = "dependencyNode$index"
            """
            |def $node = dependenciesNode.appendNode('dependency')
            |$node.appendNode('groupId', '${it.groupId}')
            |$node.appendNode('artifactId', '${it.artifactId}')
            |$node.appendNode('version', '${it.version}')
            |$node.appendNode('scope', '${it.scope}')
            """
              .trimMargin()
          }
        module.createMavenPublication(
          """
          |artifact '${module.artifactPath}'
          |pom.withXml { xml ->
          |  def dependenciesNode = xml.asNode().get('dependencies') ?: xml.asNode().appendNode('dependencies')
          |  $nodes
          |}
          """
            .trimMargin()
        )
      }
    val scriptContent =
      """
      |plugins {
      |  id 'maven-publish'
      |}
      |publishing {
      |  publications {
      |    $mavenPublications
      |  }
      |  repositories {
      |    maven { url = '${root.toUri()}' }
      |  }
      |}
      """
        .trimMargin()
    val jarsModule = "jars-module-${publishCounter++}"
    root.resolve("settings.gradle").appendText("include '$jarsModule'\n")
    root.resolve("$jarsModule/build.gradle").createFileIfNotExists().writeText(scriptContent)
  }

  private fun configureBomModules(bomModules: List<BomModule>) {
    // BOM modules are published one by one.
    bomModules.forEach { module ->
      val scriptContent =
        """
        |plugins {
        |  id 'maven-publish'
        |  id 'java-platform'
        |}
        |dependencies {
        |  constraints {
        |    ${module.dependencies.joinToString("\n") { "api '${it.coordinate}'" }}
        |  }
        |}
        |publishing {
        |  publications {
        |    ${module.createMavenPublication("from components.javaPlatform")}
        |  }
        |  repositories {
        |    maven { url = '${root.toUri()}' }
        |  }
        |}
        """
          .trimMargin()
      val pomModule = "pom-module-${publishCounter++}"
      root.resolve("settings.gradle").appendText("include '$pomModule'\n")
      root.resolve("$pomModule/build.gradle").createFileIfNotExists().writeText(scriptContent)
    }
  }

  private fun Module.createMavenPublication(block: String): String {
    return """
           |create('${coordinate.replace(":", "")}', MavenPublication) {
           |  artifactId = '$artifactId'
           |  groupId = '$groupId'
           |  version = '$version'
           |  $block
           |}
           """
      .trimMargin()
  }

  sealed class Module(groupId: String, artifactId: String, version: String) : Model() {
    val coordinate = "$groupId:$artifactId:$version"

    init {
      this.groupId = groupId
      this.artifactId = artifactId
      this.version = version
    }

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
      addDependency(dependency)
    }
  }

  inner class JarModule(groupId: String, artifactId: String, version: String) :
    Module(groupId, artifactId, version) {
    private var existingJar: Path? = null

    val artifactPath: String
      get() =
        existingJar
          ?.also {
            check(it.exists() && it.isRegularFile()) {
              "Jar file does not exist or is not a regular file: $it"
            }
          }
          ?.invariantSeparatorsPathString ?: error("No jar file provided for $coordinate")

    fun useJar(existingJar: Path) {
      this.existingJar = existingJar
    }

    fun buildJar(builder: JarBuilder.() -> Unit) {
      val jarPath = jarsDir.resolve("${coordinate.replace(':', '-')}.jar")
      existingJar = JarBuilder(jarPath).apply(builder).write()
    }
  }

  class BomModule(groupId: String, artifactId: String, version: String) :
    Module(groupId, artifactId, version) {
    init {
      packaging = "pom"
    }
  }
}

private val logger = Logging.getLogger(AppendableMavenRepository::class.java)

val Dependency.coordinate: String
  get() = "$groupId:$artifactId:$version"

private fun Path.createFileIfNotExists(): Path {
  if (!exists()) {
    createParentDirectories()
    createFile()
  }
  return this
}
