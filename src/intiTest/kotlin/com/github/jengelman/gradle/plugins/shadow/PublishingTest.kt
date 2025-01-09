package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.GradleModuleMetadata
import com.github.jengelman.gradle.plugins.shadow.util.Issue
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

  private lateinit var remoteRepo: Path

  @BeforeEach
  override fun setup() {
    super.setup()
    remoteRepo = root.resolve("remote-maven-repo")
    settingsScriptPath.appendText("rootProject.name = 'maven'" + System.lineSeparator())
  }

  @Test
  fun publishShadowJarWithMavenPublishPlugin() {
    projectScriptPath.appendText(
      publishConfiguration(
        shadowBlock = """
          archiveClassifier = ''
          archiveBaseName = 'maven-all'
        """.trimIndent(),
      ),
    )

    publish()

    assertShadowJarCommon(repoJarPath("shadow/maven-all/1.0/maven-all-1.0.jar"))
    assertPomCommon(repoPath("shadow/maven-all/1.0/maven-all-1.0.pom"))
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/860",
    "https://github.com/GradleUp/shadow/issues/945",
  )
  @Test
  fun publishShadowJarWithCustomClassifierAndExtension() {
    projectScriptPath.appendText(
      publishConfiguration(
        shadowBlock = """
          archiveClassifier = 'my-classifier'
          archiveExtension = 'my-ext'
          archiveBaseName = 'maven-all'
        """.trimIndent(),
      ),
    )

    publish()

    assertShadowJarCommon(repoJarPath("shadow/maven-all/1.0/maven-all-1.0-my-classifier.my-ext"))
    assertPomCommon(repoPath("shadow/maven-all/1.0/maven-all-1.0.pom"))
  }

  @Test
  fun publishMultiProjectShadowJarWithMavenPublishPlugin() {
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
          version = '1.0'
          group = 'shadow'
        }
      """.trimIndent(),
    )

    path("a/src/main/resources/aa.properties").writeText("aa")
    path("a/src/main/resources/aa2.properties").writeText("aa2")
    path("b/src/main/resources/bb.properties").writeText("bb")

    val publishBlock = publishConfiguration(
      dependenciesBlock = """
        implementation project(':a')
        shadow project(':b')
      """.trimIndent(),
      shadowBlock = """
        archiveClassifier = ''
        archiveBaseName = 'maven-all'
      """.trimIndent(),
    )
    path("c/build.gradle").writeText(
      """
        ${getDefaultProjectBuildScript(withGroup = true, withVersion = true)}
        $publishBlock
      """.trimIndent(),
    )

    publish()

    val publishedJar = repoJarPath("shadow/maven-all/1.0/maven-all-1.0.jar")
    assertThat(publishedJar).containsEntries(
      "aa.properties",
      "aa2.properties",
    )
    assertThat(publishedJar).doesNotContainEntries(
      "a.properties",
      "a2.properties",
      "b.properties",
      "bb.properties",
    )

    assertPomCommon(repoPath("shadow/maven-all/1.0/maven-all-1.0.pom"))
  }

  @Test
  fun publishShadowJarWithGradleMetadata() {
    projectScriptPath.appendText(
      publishConfiguration(
        projectBlock = """
          group = 'com.acme'
          version = '1.0'
        """.trimIndent(),
        dependenciesBlock = """
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
          shadow 'shadow:b:1.0'
        """.trimIndent(),
        publicationsBlock = """
          java(MavenPublication) {
            from components.java
          }
          shadow(MavenPublication) {
            from components.shadow
            artifactId = "maven-all"
          }
        """.trimIndent(),
      ),
    )

    publish()

    val entries = arrayOf("a.properties", "a2.properties", "b.properties")
    assertThat(repoJarPath("com/acme/maven/1.0/maven-1.0.jar")).doesNotContainEntries(*entries)
    assertThat(repoJarPath("com/acme/maven/1.0/maven-1.0-all.jar")).containsEntries(*entries)

    pomReader.read(repoPath("com/acme/maven/1.0/maven-1.0.pom")).let { pomContents ->
      assertThat(pomContents.dependencies.size).isEqualTo(2)
      pomContents.dependencies[0].let { dependency ->
        assertThat(dependency.groupId).isEqualTo("shadow")
        assertThat(dependency.artifactId).isEqualTo("a")
        assertThat(dependency.version).isEqualTo("1.0")
      }
      pomContents.dependencies[1].let { dependency ->
        assertThat(dependency.groupId).isEqualTo("shadow")
        assertThat(dependency.artifactId).isEqualTo("b")
        assertThat(dependency.version).isEqualTo("1.0")
      }
    }

    gmmAdapter.fromJson(repoPath("com/acme/maven/1.0/maven-1.0.module")).let { gmmContents ->
      assertThat(gmmContents.variants.size).isEqualTo(3)
      assertThat(gmmContents.variants.map { it.name }).containsOnly(
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
      assertThat(runtimeVariant.dependencies.map { it.module }).containsOnly("a", "b")

      assertShadowVariantCommon(gmmContents.variants.single { it.name == "shadowRuntimeElements" })
    }
    assertPomCommon(repoPath("com/acme/maven-all/1.0/maven-all-1.0.pom"))

    gmmAdapter.fromJson(repoPath("com/acme/maven-all/1.0/maven-all-1.0.module")).let { gmmContents ->
      assertThat(gmmContents.variants.size).isEqualTo(1)
      assertShadowVariantCommon(gmmContents.variants.single { it.name == "shadowRuntimeElements" })
    }
  }

  private fun repoPath(path: String): Path {
    return remoteRepo.resolve(path).also {
      check(it.exists()) { "Path not found: $it" }
      check(it.isRegularFile()) { "Path is not a regular file: $it" }
    }
  }

  private fun repoJarPath(path: String): JarPath {
    return JarPath(repoPath(path))
  }

  private fun publish(): BuildResult = run("publish")

  private fun publishConfiguration(
    projectBlock: String = "",
    dependenciesBlock: String = """
      implementation 'shadow:a:1.0'
      shadow 'shadow:b:1.0'
    """.trimIndent(),
    shadowBlock: String = "",
    publicationsBlock: String = """
      shadow(MavenPublication) {
        from components.shadow
        artifactId = 'maven-all'
      }
    """.trimIndent(),
  ): String {
    return """
        apply plugin: 'maven-publish'
        $projectBlock
        dependencies {
          $dependenciesBlock
        }
        $shadowJar {
          $shadowBlock
        }
        publishing {
          publications {
            $publicationsBlock
          }
          repositories {
            maven {
              url = '${remoteRepo.toUri()}'
            }
          }
        }
    """.trimIndent()
  }

  private fun assertPomCommon(pomPath: Path) {
    val contents = pomReader.read(pomPath)
    assertThat(contents.dependencies.size).isEqualTo(1)

    val dependency = contents.dependencies[0]
    assertThat(dependency.groupId).isEqualTo("shadow")
    assertThat(dependency.artifactId).isEqualTo("b")
    assertThat(dependency.version).isEqualTo("1.0")
  }

  private fun assertShadowVariantCommon(variant: GradleModuleMetadata.Variant) {
    assertThat(variant.attributes[Usage.USAGE_ATTRIBUTE.name]).isEqualTo(Usage.JAVA_RUNTIME)
    assertThat(variant.attributes[Bundling.BUNDLING_ATTRIBUTE.name]).isEqualTo(Bundling.SHADOWED)
    assertThat(variant.dependencies.size).isEqualTo(1)
    assertThat(variant.dependencies.map { it.module }).containsOnly("b")
  }

  private fun assertShadowJarCommon(jarPath: JarPath) {
    assertThat(jarPath).all {
      containsEntries(
        "a.properties",
        "a2.properties",
      )
      doesNotContainEntries(
        "b.properties",
      )
    }
  }

  private companion object {
    fun MavenXpp3Reader.read(path: Path): Model = read(path.inputStream())

    fun <T : Any> JsonAdapter<T>.fromJson(path: Path): T = requireNotNull(fromJson(path.readText()))
  }
}
