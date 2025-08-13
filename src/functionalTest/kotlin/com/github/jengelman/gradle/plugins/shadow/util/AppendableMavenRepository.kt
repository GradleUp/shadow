package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.BasePluginTest.Companion.commonArguments
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
import org.gradle.testkit.runner.GradleRunner

class AppendableMavenRepository(
  val root: Path,
  private val gradleRunner: GradleRunner,
) {
  private val modules = mutableListOf<Module>()
  private val jarsDir: Path

  init {
    check(root.exists()) { "Maven repository root directory does not exist: $root" }

    root.resolve("settings.gradle").createFile()
      .writeText("rootProject.name = '${root.name}'$lineSeparator")
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

  fun publish() {
    check(modules.isNotEmpty()) {
      "No modules to publish. Please add at least one module."
    }
    modules.groupBy { it::class }.forEach { (type, group) ->
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

    gradleRunner.withProjectDir(root.toFile())
      .withArguments(commonArguments + "publish")
      .build()
    logger.info(
      """
        Publish modules to Maven repository at ${root.toUri()}:
        ${modules.joinToString(lineSeparator) { it.coordinate }}
      """.trimIndent(),
    )
    modules.clear()
  }

  private fun configureJarModules(jarModules: List<JarModule>) {
    val mavenPublications = jarModules.joinToString(lineSeparator) { module ->
      var index = -1
      val nodes = module.dependencies.joinToString(lineSeparator) {
        index++
        val node = "dependencyNode$index"
        """
          def $node = dependenciesNode.appendNode('dependency')
          $node.appendNode('groupId', '${it.groupId}')
          $node.appendNode('artifactId', '${it.artifactId}')
          $node.appendNode('version', '${it.version}')
          $node.appendNode('scope', '${it.scope}')
        """.trimIndent()
      }
      module.createMavenPublication(
        """
          artifact '${module.artifactPath}'
          pom.withXml { xml ->
            def dependenciesNode = xml.asNode().get('dependencies') ?: xml.asNode().appendNode('dependencies')
            $nodes
          }
        """.trimIndent(),
      )
    }
    val scriptContent = """
      plugins {
        id 'maven-publish'
      }
      publishing {
        publications {
          $mavenPublications
        }
        repositories {
          maven { url = '${root.toUri()}' }
        }
      }
    """.trimIndent()
    val jarsModule = "jars-module"
    root.resolve("settings.gradle").appendText("include '$jarsModule'$lineSeparator")
    root.resolve("$jarsModule/build.gradle")
      .createFileIfNotExists()
      .writeText(scriptContent)
  }

  private fun configureBomModules(bomModules: List<BomModule>) {
    // BOM modules are published one by one.
    bomModules.forEachIndexed { index, module ->
      val scriptContent = """
        plugins {
          id 'maven-publish'
          id 'java-platform'
        }
        dependencies {
          constraints {
            ${module.dependencies.joinToString(lineSeparator) { "api '${it.coordinate}'" }}
          }
        }
        publishing {
          publications {
            ${module.createMavenPublication("from components.javaPlatform")}
          }
          repositories {
            maven { url = '${root.toUri()}' }
          }
        }
      """.trimIndent()
      val pomModule = "pom-module-$index"
      root.resolve("settings.gradle").appendText("include '$pomModule'$lineSeparator")
      root.resolve("$pomModule/build.gradle")
        .createFileIfNotExists()
        .writeText(scriptContent)
    }
  }

  private fun Module.createMavenPublication(
    block: String,
  ): String {
    return """
      create('${coordinate.replace(":", "")}', MavenPublication) {
        artifactId = '$artifactId'
        groupId = '$groupId'
        version = '$version'
        $block
      }
    """.trimIndent()
  }

  sealed class Module(
    groupId: String,
    artifactId: String,
    version: String,
  ) : Model() {
    val coordinate = "$groupId:$artifactId:$version"

    init {
      this.groupId = groupId
      this.artifactId = artifactId
      this.version = version
    }

    fun addDependency(groupId: String, artifactId: String, version: String, scope: String = "runtime") {
      val dependency = Dependency().also {
        it.groupId = groupId
        it.artifactId = artifactId
        it.version = version
        it.scope = scope
      }
      addDependency(dependency)
    }

    fun addDependency(coordinate: String, scope: String = "runtime") {
      val parts = coordinate.split(":")
      require(parts.size == 3) {
        "Invalid coordinate format: '$coordinate'. Expected format is 'groupId:artifactId:version'."
      }
      val (groupId, artifactId, version) = parts
      addDependency(groupId, artifactId, version, scope)
    }
  }

  inner class JarModule(
    groupId: String,
    artifactId: String,
    version: String,
  ) : Module(groupId, artifactId, version) {
    private var existingJar: Path? = null

    val artifactPath: String
      get() = existingJar?.also {
        check(it.exists() && it.isRegularFile()) { "Jar file does not exist or is not a regular file: $it" }
      }?.invariantSeparatorsPathString ?: error("No jar file provided for $coordinate")

    fun useJar(existingJar: Path) {
      this.existingJar = existingJar
    }

    fun buildJar(builder: JarBuilder.() -> Unit) {
      val jarPath = jarsDir.resolve(coordinate.replace(":", "-") + ".jar")
      existingJar = JarBuilder(jarPath).apply(builder).write()
    }
  }

  class BomModule(
    groupId: String,
    artifactId: String,
    version: String,
  ) : Module(groupId, artifactId, version) {
    init {
      packaging = "pom"
    }
  }
}

private val logger = Logging.getLogger(AppendableMavenRepository::class.java)

private val lineSeparator = System.lineSeparator()

val Dependency.coordinate: String get() = "$groupId:$artifactId:$version"

private fun Path.createFileIfNotExists(): Path {
  if (!exists()) {
    createParentDirectories()
    createFile()
  }
  return this
}
