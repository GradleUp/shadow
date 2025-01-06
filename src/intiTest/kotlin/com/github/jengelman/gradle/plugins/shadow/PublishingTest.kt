package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import com.github.jengelman.gradle.plugins.shadow.util.GradleModuleMetadata
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Usage
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PublishingTest : BasePluginTest() {
  private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
  private val gmmAdapter = moshi.adapter(GradleModuleMetadata::class.java)
  private val pomReader = MavenXpp3Reader()

  private lateinit var publishingRepo: AppendableMavenFileRepository

  @BeforeEach
  override fun setup() {
    super.setup()
    publishingRepo = repo("remote-repo")

    publishArtifactA()
    publishArtifactB()

    settingsScriptPath.appendText("rootProject.name = 'maven'" + System.lineSeparator())
    projectScriptPath.appendText(
      """
        apply plugin: 'maven-publish'

        dependencies {
          implementation 'shadow:a:1.0'
          shadow 'shadow:b:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
    )
  }

  @Test
  fun publishShadowJarWithMavenPublishPlugin() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          archiveClassifier = ''
          archiveBaseName = 'maven-all'
        }
        publishing {
          publications {
            shadow(MavenPublication) {
              from components.shadow
              artifactId = 'maven-all'
            }
          }
          repositories {
            maven {
              url = "${publishingRepo.uri}"
            }
          }
        }
      """.trimIndent(),
    )

    publish()

    val publishedJar = repoJarPath("shadow/maven-all/1.0/maven-all-1.0.jar")
    assertThat(publishedJar).containsEntries(
      "a.properties",
      "a2.properties",
    )

    val contents = pomReader.read(repoPath("shadow/maven-all/1.0/maven-all-1.0.pom"))
    assertThat(contents.dependencies.size).isEqualTo(1)

    val dependency = contents.dependencies[0]
    assertThat(dependency.groupId).isEqualTo("shadow")
    assertThat(dependency.artifactId).isEqualTo("b")
    assertThat(dependency.version).isEqualTo("1.0")
  }

  @Test
  fun publishShadowJarWithCustomClassifierAndExtension() {
    projectScriptPath.appendText(
      """
        apply plugin: 'maven-publish'

        publishing {
          publications {
            shadow(MavenPublication) { publication ->
              project.shadow.component(publication)
              artifactId = 'maven-all'
            }
          }
          repositories {
            maven {
              url = "${publishingRepo.uri}"
            }
          }
        }
        $shadowJar {
          archiveClassifier = 'my-classifier'
          archiveExtension = 'my-ext'
          archiveBaseName = 'maven-all'
        }
      """.trimIndent(),
    )

    publish()

    val publishedJar = repoJarPath("shadow/maven-all/1.0/maven-all-1.0-my-classifier.my-ext")
    assertThat(publishedJar).containsEntries(
      "a.properties",
      "a2.properties",
    )
  }

  @Test
  fun publishMultiprojectShadowJarWithMavenPublishPlugin() {
    settingsScriptPath.appendText(
      """
        include 'a', 'b', 'c'
      """.trimIndent(),
    )
    projectScriptPath.writeText(
      """
        subprojects {
          apply plugin: 'java'
          apply plugin: 'maven-publish'

          version = "1.0"
          group = 'shadow'

          publishing {
            repositories {
              maven {
                url = "${publishingRepo.uri}"
              }
            }
          }
        }
      """.trimIndent(),
    )

    path("a/src/main/resources/a.properties").writeText("a")
    path("a/src/main/resources/a2.properties").writeText("a2")
    path("b/src/main/resources/b.properties").writeText("b")

    path("c/build.gradle").writeText(
      """
        plugins {
          id 'com.gradleup.shadow'
        }
        dependencies {
          implementation project(':a')
          shadow project(':b')
        }
        $shadowJar {
          archiveClassifier = ''
          archiveBaseName = 'maven-all'
        }
        publishing {
          publications {
            shadow(MavenPublication) {
              from components.shadow
              artifactId = 'maven-all'
            }
          }
        }
      """.trimIndent(),
    )

    publish()

    val publishedJar = repoJarPath("shadow/maven-all/1.0/maven-all-1.0.jar")
    assertThat(publishedJar).containsEntries(
      "a.properties",
      "a2.properties",
    )

    val contents = pomReader.read(repoPath("shadow/maven-all/1.0/maven-all-1.0.pom"))
    assertThat(contents.dependencies.size).isEqualTo(1)

    val dependency = contents.dependencies[0]
    assertThat(dependency.groupId).isEqualTo("shadow")
    assertThat(dependency.artifactId).isEqualTo("b")
    assertThat(dependency.version).isEqualTo("1.0")
  }

  @Test
  fun publishShadowJarWithGradleMetadata() {
    projectScriptPath.appendText(
      """
        apply plugin: 'maven-publish'
        dependencies {
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
          shadow 'shadow:b:1.0'
        }
        group = 'com.acme'
        version = '1.0'
        publishing {
          publications {
            java(MavenPublication) {
              from components.java
            }
            shadow(MavenPublication) {
              from components.shadow
              artifactId = "maven-all"
            }
          }
          repositories {
            maven {
              url = "${publishingRepo.uri}"
            }
          }
        }
      """.trimIndent(),
    )

    publish()

    val entries = arrayOf("a.properties", "a2.properties")
    assertThat(repoJarPath("com/acme/maven/1.0/maven-1.0.jar")).doesNotContainEntries(*entries)
    assertThat(repoJarPath("com/acme/maven/1.0/maven-1.0-all.jar")).containsEntries(*entries)

    val pomContents = pomReader.read(repoPath("com/acme/maven/1.0/maven-1.0.pom"))
    assertThat(pomContents.dependencies.size).isEqualTo(2)

    val dependency1 = pomContents.dependencies[0]
    assertThat(dependency1.groupId).isEqualTo("shadow")
    assertThat(dependency1.artifactId).isEqualTo("a")
    assertThat(dependency1.version).isEqualTo("1.0")

    val dependency2 = pomContents.dependencies[1]
    assertThat(dependency2.groupId).isEqualTo("shadow")
    assertThat(dependency2.artifactId).isEqualTo("b")
    assertThat(dependency2.version).isEqualTo("1.0")

    val gmmContents = gmmAdapter.fromJson(repoPath("com/acme/maven/1.0/maven-1.0.module"))
    assertThat(gmmContents.variants.size).isEqualTo(3)
    assertThat(gmmContents.variants.map { it.name }.toSet()).containsOnly(
      "apiElements",
      "runtimeElements",
      "shadowRuntimeElements",
    )

    val apiVariant = gmmContents.variants.single { it.name == "apiElements" }
    assertThat(apiVariant.attributes[Usage.USAGE_ATTRIBUTE.name]).isEqualTo(Usage.JAVA_API)
    assertThat(apiVariant.attributes[Bundling.BUNDLING_ATTRIBUTE.name]).isEqualTo(Bundling.EXTERNAL)
    assertThat(apiVariant.dependencies).isEmpty()

    val runtimeVariant = gmmContents.variants.single { it.name == "runtimeElements" }
    assertThat(runtimeVariant.attributes[Usage.USAGE_ATTRIBUTE.name]).isEqualTo(Usage.JAVA_RUNTIME)
    assertThat(runtimeVariant.attributes[Bundling.BUNDLING_ATTRIBUTE.name]).isEqualTo(Bundling.EXTERNAL)
    assertThat(runtimeVariant.dependencies.size).isEqualTo(2)
    assertThat(runtimeVariant.dependencies.map { it.module }.toSet()).containsOnly(
      "a",
      "b",
    )

    val shadowRuntimeVariant = gmmContents.variants.single { it.name == "shadowRuntimeElements" }
    assertThat(shadowRuntimeVariant.attributes[Usage.USAGE_ATTRIBUTE.name]).isEqualTo(Usage.JAVA_RUNTIME)
    assertThat(shadowRuntimeVariant.attributes[Bundling.BUNDLING_ATTRIBUTE.name]).isEqualTo(Bundling.SHADOWED)
    assertThat(shadowRuntimeVariant.dependencies.size).isEqualTo(1)
    assertThat(shadowRuntimeVariant.dependencies[0].module).isEqualTo("b")

    assertThat(repoJarPath("com/acme/maven-all/1.0/maven-all-1.0-all.jar")).containsEntries(
      "a.properties",
      "a2.properties",
    )
    val shadowPomContents = pomReader.read(repoPath("com/acme/maven-all/1.0/maven-all-1.0.pom"))
    assertThat(shadowPomContents.dependencies.size).isEqualTo(1)
    val shadowDependency = shadowPomContents.dependencies[0]
    assertThat(shadowDependency.groupId).isEqualTo("shadow")
    assertThat(shadowDependency.artifactId).isEqualTo("b")
    assertThat(shadowDependency.version).isEqualTo("1.0")

    val shadowGmmContents = gmmAdapter.fromJson(repoPath("com/acme/maven-all/1.0/maven-all-1.0.module"))
    assertThat(shadowGmmContents.variants.size).isEqualTo(1)
    assertThat(shadowGmmContents.variants.map { it.name }.toSet()).containsOnly("shadowRuntimeElements")

    val shadowRuntimeVariant2 = shadowGmmContents.variants.single { it.name == "shadowRuntimeElements" }
    assertThat(shadowRuntimeVariant2.attributes[Usage.USAGE_ATTRIBUTE.name]).isEqualTo(Usage.JAVA_RUNTIME)
    assertThat(shadowRuntimeVariant2.attributes[Bundling.BUNDLING_ATTRIBUTE.name]).isEqualTo(Bundling.SHADOWED)
    assertThat(shadowRuntimeVariant2.dependencies.size).isEqualTo(1)
    assertThat(shadowRuntimeVariant2.dependencies[0].module).isEqualTo("b")
  }

  private fun repoPath(path: String): Path {
    return publishingRepo.rootDir.resolve(path).also {
      check(it.exists()) { "Path not found: $it" }
      check(it.isRegularFile()) { "Path is not a regular file: $it" }
    }
  }

  private fun repoJarPath(path: String): JarPath {
    return JarPath(repoPath(path))
  }

  private fun publish(): BuildResult = run("publish")

  private companion object {
    fun MavenXpp3Reader.read(path: Path): Model = read(path.inputStream())

    fun <T : Any> JsonAdapter<T>.fromJson(path: Path): T = requireNotNull(fromJson(path.readText()))
  }
}
