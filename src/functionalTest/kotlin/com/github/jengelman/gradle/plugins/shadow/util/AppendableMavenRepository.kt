package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.BasePluginTest.Companion.commonArguments
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.writeText
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.testkit.runner.GradleRunner

class AppendableMavenRepository(
  val root: Path,
  private val gradleRunner: GradleRunner,
) {
  private val projectBuildScript: Path
  private val modules = mutableListOf<Module>()

  init {
    root.resolve("temp").createDirectories()
    root.resolve("settings.gradle").createFile()
      .writeText("rootProject.name = '${root.name}'")
    projectBuildScript = root.resolve("build.gradle").createFile()
  }

  fun jarModule(
    groupId: String,
    artifactId: String,
    version: String,
    action: JarModule.() -> Unit,
  ) = apply {
    modules += JarModule(groupId, artifactId, version).also(action)
  }

  fun bomModule(
    groupId: String,
    artifactId: String,
    version: String,
    action: BomModule.() -> Unit,
  ) = apply {
    modules += BomModule(groupId, artifactId, version).also(action)
  }

  fun publish() {
    if (modules.isEmpty()) return
    projectBuildScript.writeText(
      """
        plugins {
          id 'maven-publish'
        }
        publishing {
          publications {
            ${modules.joinToString(lineSeparator) { createPublication(it) }}
          }
          repositories {
            maven { url = '${root.toUri()}' }
          }
        }
      """.trimIndent(),
    )
    gradleRunner.withProjectDir(root.toFile()).withArguments(commonArguments + "publish").build()
    modules.clear()
  }

  private fun createPublication(module: Module) = with(module) {
    val outputJar = (module as JarModule).build()
    val pubName = outputJar.name.replace(".", "")

    var index = -1
    val nodes = dependencies.joinToString(lineSeparator) {
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

    """
      create('$pubName', MavenPublication) {
        artifactId = '$artifactId'
        groupId = '$groupId'
        version = '$version'
        artifact '${outputJar.invariantSeparatorsPathString}'
        pom.withXml { xml ->
          def dependenciesNode = xml.asNode().get('dependencies') ?: xml.asNode().appendNode('dependencies')
          $nodes
        }
      }
    """.trimIndent() + lineSeparator
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
  }

  inner class JarModule(
    groupId: String,
    artifactId: String,
    version: String,
  ) : Module(groupId, artifactId, version) {
    private lateinit var existingJar: Path

    fun useJar(existingJar: Path) {
      this.existingJar = existingJar
    }

    fun buildJar(builder: JarBuilder.() -> Unit) {
      val jarName = coordinate.replace(":", "-") + ".jar"
      existingJar = JarBuilder(root.resolve("temp/$jarName")).apply(builder).write()
    }

    fun build(): Path {
      check(::existingJar.isInitialized) { "No jar file provided for $coordinate" }
      return existingJar.also {
        check(it.exists()) { "Jar file doesn't exist for $coordinate in: $it" }
        check(it.isRegularFile()) { "Jar is not a regular file for $coordinate in: $it" }
      }
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

private val lineSeparator = System.lineSeparator()

val Dependency.coordinate: String get() = "$groupId:$artifactId:$version"
