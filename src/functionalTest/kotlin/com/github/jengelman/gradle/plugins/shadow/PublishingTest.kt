package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME
import com.github.jengelman.gradle.plugins.shadow.util.BooleanParameterizedTest
import com.github.jengelman.gradle.plugins.shadow.util.GradleModuleMetadata
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PublishingTest : BasePluginTest() {
  private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
  private val gmmAdapter = moshi.adapter(GradleModuleMetadata::class.java)
  private val pomReader = MavenXpp3Reader()

  @TempDir
  lateinit var remoteRepoPath: Path

  @BeforeEach
  override fun setup() {
    super.setup()
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

  @Test
  fun publishShadowJarInsteadOfJarWithMavenPublishPlugin() {
    projectScriptPath.appendText(
      publishConfiguration(
        shadowBlock = """
          archiveClassifier = ''
        """.trimIndent(),
        publicationsBlock = """
          shadow(MavenPublication) {
            from components.shadow
          }
        """.trimIndent(),
      ),
    )

    publish()

    assertShadowJarCommon(repoJarPath("shadow/maven/1.0/maven-1.0.jar"))
    assertPomCommon(repoPath("shadow/maven/1.0/maven-1.0.pom"))
    assertShadowVariantCommon(gmmAdapter.fromJson(repoPath("shadow/maven/1.0/maven-1.0.module")))
  }

  @BooleanParameterizedTest
  fun publishShadowedGradlePluginWithMavenPublishPlugin(legacy: Boolean) {
    writeGradlePluginModule(legacy)
    projectScriptPath.appendText(
      publishConfiguration(
        projectBlock = """
          apply plugin: 'com.gradle.plugin-publish'
          group = 'my.plugin'
          version = '1.0'
        """.trimIndent(),
        shadowBlock = """
          archiveClassifier = ''
        """.trimIndent(),
        publicationsBlock = """
          pluginMaven(MavenPublication) {
            artifactId = 'my-gradle-plugin'
          }
        """.trimIndent(),
      ),
    )

    publish()

    val artifactRoot = "my/plugin/my-gradle-plugin/1.0"
    assertThat(repoPath(artifactRoot).listDirectoryEntries("*.jar").map(Path::name)).containsOnly(
      "my-gradle-plugin-1.0.jar",
      "my-gradle-plugin-1.0-javadoc.jar",
      "my-gradle-plugin-1.0-sources.jar",
    )

    val artifactPrefix = "$artifactRoot/my-gradle-plugin-1.0"
    assertShadowJarCommon(repoJarPath("$artifactPrefix.jar"))
    assertPomCommon(repoPath("$artifactPrefix.pom"))
    assertShadowVariantCommon(gmmAdapter.fromJson(repoPath("$artifactPrefix.module")))
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

    assertThat(repoJarPath("shadow/maven-all/1.0/maven-all-1.0.jar")).useAll {
      containsEntries(
        "aa.properties",
        "aa2.properties",
      )
      doesNotContainEntries(
        "a.properties",
        "a2.properties",
        "b.properties",
        "bb.properties",
      )
    }
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
    assertThat(repoJarPath("com/acme/maven/1.0/maven-1.0.jar")).useAll {
      doesNotContainEntries(*entries)
    }
    assertThat(repoJarPath("com/acme/maven/1.0/maven-1.0-all.jar")).useAll {
      containsEntries(*entries)
    }

    pomReader.read(repoPath("com/acme/maven/1.0/maven-1.0.pom")).let { pom ->
      assertThat(pom.dependencies.size).isEqualTo(2)
      pom.dependencies[0].let { dependency ->
        assertThat(dependency.groupId).isEqualTo("shadow")
        assertThat(dependency.artifactId).isEqualTo("a")
        assertThat(dependency.version).isEqualTo("1.0")
      }
      pom.dependencies[1].let { dependency ->
        assertThat(dependency.groupId).isEqualTo("shadow")
        assertThat(dependency.artifactId).isEqualTo("b")
        assertThat(dependency.version).isEqualTo("1.0")
      }
    }
    gmmAdapter.fromJson(repoPath("com/acme/maven/1.0/maven-1.0.module")).let { gmm ->
      // apiElements, runtimeElements, shadowRuntimeElements
      assertThat(gmm.variants.map { it.name }).containsOnly(
        API_ELEMENTS_CONFIGURATION_NAME,
        RUNTIME_ELEMENTS_CONFIGURATION_NAME,
        SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
      )
      assertThat(gmm.variants.single { it.name == API_ELEMENTS_CONFIGURATION_NAME }).all {
        transform { it.attributes }.all {
          contains(Category.CATEGORY_ATTRIBUTE.name, Category.LIBRARY)
          contains(Bundling.BUNDLING_ATTRIBUTE.name, Bundling.EXTERNAL)
          contains(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name, LibraryElements.JAR)
          contains(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_API)
        }
        transform { it.dependencies }.isEmpty()
      }
      assertThat(gmm.variants.single { it.name == RUNTIME_ELEMENTS_CONFIGURATION_NAME }).all {
        transform { it.attributes }.all {
          contains(Category.CATEGORY_ATTRIBUTE.name, Category.LIBRARY)
          contains(Bundling.BUNDLING_ATTRIBUTE.name, Bundling.EXTERNAL)
          contains(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name, LibraryElements.JAR)
          contains(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_RUNTIME)
        }
        transform { it.dependencies.map { dep -> dep.module } }.containsOnly("a", "b")
      }
      assertShadowVariantCommon(gmm)
    }

    assertPomCommon(repoPath("com/acme/maven-all/1.0/maven-all-1.0.pom"))
    gmmAdapter.fromJson(repoPath("com/acme/maven-all/1.0/maven-all-1.0.module")).let { gmm ->
      assertThat(gmm.variants.map { it.name }).containsOnly(
        SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
      )
      assertShadowVariantCommon(gmm)
    }
  }

  private fun repoPath(relative: String): Path {
    return remoteRepoPath.resolve(relative).also {
      check(it.exists()) { "Path not found: $it" }
    }
  }

  private fun repoJarPath(relative: String): JarPath {
    return JarPath(remoteRepoPath.resolve(relative))
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
              url = '${remoteRepoPath.toUri()}'
            }
          }
        }
    """.trimIndent()
  }

  private fun assertPomCommon(pomPath: Path) {
    val pom = pomReader.read(pomPath)
    val dependency = pom.dependencies.single()
    assertThat(dependency.groupId).isEqualTo("shadow")
    assertThat(dependency.artifactId).isEqualTo("b")
    assertThat(dependency.version).isEqualTo("1.0")
  }

  private fun assertShadowVariantCommon(gmm: GradleModuleMetadata) {
    assertThat(gmm.variants.single { it.name == SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME }).all {
      transform { it.attributes }.all {
        contains(Category.CATEGORY_ATTRIBUTE.name, Category.LIBRARY)
        contains(Bundling.BUNDLING_ATTRIBUTE.name, Bundling.SHADOWED)
        contains(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name, LibraryElements.JAR)
        contains(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_RUNTIME)
      }
      transform { it.dependencies.map { dep -> dep.module } }.containsOnly("b")
    }
  }

  private fun assertShadowJarCommon(jarPath: JarPath) {
    assertThat(jarPath).useAll {
      containsEntries(
        "a.properties",
        "a2.properties",
      )
      doesNotContainEntries(
        "b.properties",
      )
      getMainAttr("Class-Path").isEqualTo("b-1.0.jar")
    }
  }

  private companion object {
    fun MavenXpp3Reader.read(path: Path): Model = path.inputStream().use { read(it) }

    fun <T : Any> JsonAdapter<T>.fromJson(path: Path): T = requireNotNull(fromJson(path.readText()))
  }
}
