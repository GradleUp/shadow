package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.BasePluginTest.Companion.commonArguments
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
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

  fun module(
    groupId: String,
    artifactId: String,
    version: String,
    action: Module.() -> Unit,
  ) = apply {
    modules += Module(groupId, artifactId, version).also(action)
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
            ${modules.joinToString(System.lineSeparator()) { createPublication(it) }}
          }
          repositories {
            maven {
              url = '${root.toUri()}'
            }
          }
        }
      """.trimIndent(),
    )
    gradleRunner.withProjectDir(root.toFile()).withArguments(commonArguments + "publish").build()
    modules.clear()
  }

  private fun createPublication(module: Module) = with(module) {
    val outputJar = build()
    val pubName = outputJar.name.replace(".", "")

    var index = -1
    val nodes = dependencies.joinToString(System.lineSeparator()) {
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
        artifact '${outputJar.toUri().toURL().path}'
        pom.withXml { xml ->
          def dependenciesNode = xml.asNode().get('dependencies') ?: xml.asNode().appendNode('dependencies')
          $nodes
        }
      }
    """.trimIndent() + System.lineSeparator()
  }

  inner class Module(
    groupId: String,
    artifactId: String,
    version: String,
  ) : Model() {
    private val coordinate = "$groupId:$artifactId:$version"
    private lateinit var existingJar: Path

    init {
      this.groupId = groupId
      this.artifactId = artifactId
      this.version = version
    }

    fun useJar(existingJar: Path) {
      this.existingJar = existingJar
    }

    fun buildJar(builder: JarBuilder.() -> Unit) {
      val jarName = coordinate.replace(":", "-") + ".jar"
      existingJar = JarBuilder(root.resolve("temp/$jarName")).apply(builder).write()
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

    fun build(): Path {
      if (!::existingJar.isInitialized) error("No jar file provided for $coordinate")
      return existingJar.also {
        check(it.exists()) { "Jar file doesn't exist for $coordinate in: $it" }
        check(it.isRegularFile()) { "Jar is not a regular file for $coordinate in: $it" }
      }
    }
  }
}
