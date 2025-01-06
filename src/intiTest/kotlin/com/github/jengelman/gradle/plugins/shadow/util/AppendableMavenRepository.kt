package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.BasePluginTest.Companion.commonArguments
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
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
    root.createDirectories()
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
    val outputJar = build(root)
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

  class Module(
    groupId: String,
    artifactId: String,
    version: String,
  ) : Model() {
    private val contents = mutableMapOf<String, String>()
    private var existingJar: Path? = null

    init {
      this.groupId = groupId
      this.artifactId = artifactId
      this.version = version
    }

    fun useJar(existingJar: Path) {
      this.existingJar = existingJar
    }

    fun insert(entry: String, content: String) {
      contents[entry] = content
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

    fun build(root: Path): Path {
      return existingJar ?: JarBuilder(root.resolve("$groupId-$artifactId-$version.jar"), contents).write()
    }
  }
}
