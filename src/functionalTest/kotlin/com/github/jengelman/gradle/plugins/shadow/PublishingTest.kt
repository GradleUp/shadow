package com.github.jengelman.gradle.plugins.shadow

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.single
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME
import com.github.jengelman.gradle.plugins.shadow.util.BooleanParameterizedTest
import com.github.jengelman.gradle.plugins.shadow.util.GradleModuleMetadata
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import com.github.jengelman.gradle.plugins.shadow.util.gavs
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
import org.gradle.api.JavaVersion
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PublishingTest : BasePluginTest() {
  @TempDir
  lateinit var remoteRepoPath: Path

  @BeforeEach
  override fun setup() {
    super.setup()
    settingsScriptPath.appendText("rootProject.name = 'maven'" + System.lineSeparator())
  }

  @Test
  fun publishShadowJar() {
    projectScriptPath.appendText(
      publishConfiguration(
        shadowBlock = """
          archiveClassifier = ''
          archiveBaseName = 'maven-all'
        """.trimIndent(),
      ) + System.lineSeparator(),
    )

    publish()

    val assertions = { variantAttrs: Array<Pair<String, String>>? ->
      assertShadowJarCommon(repoJarPath("shadow/maven-all/1.0/maven-all-1.0.jar"))
      assertPomCommon(repoPath("shadow/maven-all/1.0/maven-all-1.0.pom"))
      val gmm = gmmAdapter.fromJson(repoPath("shadow/maven-all/1.0/maven-all-1.0.module"))
      if (variantAttrs == null) {
        assertShadowVariantCommon(gmm)
      } else {
        assertShadowVariantCommon(gmm, variantAttrs = variantAttrs)
      }
    }

    assertions(null)

    val attrsWithoutTargetJvm = shadowVariantAttrs.filterNot { (name, _) ->
      name == TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name
    }.toTypedArray()
    val targetJvmAttr17 = TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name to "17"
    val targetJvmAttr11 = TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name to "11"

    projectScriptPath.appendText(
      """
        java {
          toolchain.languageVersion = JavaLanguageVersion.of(17)
        }
      """.trimIndent() + System.lineSeparator(),
    )
    publish()
    assertions(attrsWithoutTargetJvm + targetJvmAttr17)

    projectScriptPath.appendText(
      """
        java {
          targetCompatibility = JavaVersion.VERSION_11
        }
      """.trimIndent() + System.lineSeparator(),
    )
    publish()
    assertions(attrsWithoutTargetJvm + targetJvmAttr11)

    projectScriptPath.appendText(
      """
        java {
          sourceCompatibility = JavaVersion.VERSION_1_8
        }
      """.trimIndent() + System.lineSeparator(),
    )
    publish()
    // sourceCompatibility doesn't affect the target JVM version.
    assertions(attrsWithoutTargetJvm + targetJvmAttr11)
  }

  @Test
  fun publishShadowJarInsteadOfJar() {
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
  fun publishShadowedGradlePlugin(legacy: Boolean) {
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
    assertShadowVariantCommon(gmmAdapter.fromJson(repoPath("shadow/maven-all/1.0/maven-all-1.0.module")))
  }

  @Test
  fun publishMultiProjectShadowJar() {
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
    assertShadowVariantCommon(gmmAdapter.fromJson(repoPath("shadow/maven-all/1.0/maven-all-1.0.module")))
  }

  @Test
  fun publishJarThatDependsOnShadowJar() {
    writeClientAndServerModules(clientShadowed = true)
    path("client/build.gradle").appendText(
      publishingBlock(
        projectBlock = "group = 'example'",
        publicationsBlock = """
          shadow(MavenPublication) {
            from components.shadow
          }
        """.trimIndent(),
      ),
    )
    path("server/build.gradle").appendText(
      publishingBlock(
        projectBlock = "group = 'example'",
        publicationsBlock = """
          java(MavenPublication) {
            from components.java
          }
        """.trimIndent(),
      ),
    )

    publish()

    gmmAdapter.fromJson(repoPath("example/server/1.0/server-1.0.module")).let { gmm ->
      assertThat(gmm.variantNames).containsOnly(
        API_ELEMENTS_CONFIGURATION_NAME,
        RUNTIME_ELEMENTS_CONFIGURATION_NAME,
        SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
      )
      assertThat(gmm.runtimeElementsVariant.gavs).containsOnly(
        "example:client:1.0",
      )
      assertThat(gmm.shadowRuntimeElementsVariant.gavs).isEmpty()
      assertShadowVariantCommon(gmm, gavs = emptyArray()) {
        transform { it.fileNames }.single().isEqualTo("server-1.0-all.jar")
      }
    }
    gmmAdapter.fromJson(repoPath("example/client/1.0/client-1.0.module")).let { gmm ->
      assertThat(gmm.variantNames).containsOnly(
        SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
      )
      assertShadowVariantCommon(gmm, gavs = emptyArray()) {
        transform { it.fileNames }.single().isEqualTo("client-1.0-all.jar")
      }
    }
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

    assertPomCommon(repoPath("com/acme/maven/1.0/maven-1.0.pom"), arrayOf("shadow:a:1.0", "shadow:b:1.0"))
    gmmAdapter.fromJson(repoPath("com/acme/maven/1.0/maven-1.0.module")).let { gmm ->
      // apiElements, runtimeElements, shadowRuntimeElements
      assertThat(gmm.variantNames).containsOnly(
        API_ELEMENTS_CONFIGURATION_NAME,
        RUNTIME_ELEMENTS_CONFIGURATION_NAME,
        SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
      )
      assertThat(gmm.apiElementsVariant).all {
        transform { it.attributes }.containsOnly(
          *commonVariantAttrs,
          Bundling.BUNDLING_ATTRIBUTE.name to Bundling.EXTERNAL,
          Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_API,
        )
        transform { it.gavs }.isEmpty()
      }
      assertThat(gmm.runtimeElementsVariant).all {
        transform { it.attributes }.containsOnly(
          *commonVariantAttrs,
          Bundling.BUNDLING_ATTRIBUTE.name to Bundling.EXTERNAL,
          Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_RUNTIME,
        )
        transform { it.gavs }.containsOnly(
          "shadow:a:1.0",
          "shadow:b:1.0",
        )
      }
      assertShadowVariantCommon(gmm)
    }

    assertPomCommon(repoPath("com/acme/maven-all/1.0/maven-all-1.0.pom"))
    gmmAdapter.fromJson(repoPath("com/acme/maven-all/1.0/maven-all-1.0.module")).let { gmm ->
      assertThat(gmm.variantNames).containsOnly(
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
        dependencies {
          $dependenciesBlock
        }
        $shadowJar {
          $shadowBlock
        }
        ${publishingBlock(projectBlock = projectBlock, publicationsBlock = publicationsBlock)}
    """.trimIndent()
  }

  private fun publishingBlock(
    projectBlock: String,
    publicationsBlock: String,
  ): String {
    return """
      apply plugin: 'maven-publish'
      $projectBlock
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

  private fun assertPomCommon(
    pomPath: Path,
    gavs: Array<String> = arrayOf("shadow:b:1.0"),
  ) {
    assertThat(pomReader.read(pomPath).gavs).containsOnly(*gavs)
  }

  private fun assertShadowVariantCommon(
    gmm: GradleModuleMetadata,
    variantAttrs: Array<Pair<String, String>> = shadowVariantAttrs,
    gavs: Array<String> = arrayOf("shadow:b:1.0"),
    body: Assert<GradleModuleMetadata.Variant>.() -> Unit = {},
  ) {
    assertThat(gmm.shadowRuntimeElementsVariant).all {
      transform { it.attributes }.containsOnly(*variantAttrs)
      transform { it.gavs }.containsOnly(*gavs)
      body()
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
    val gmmAdapter: JsonAdapter<GradleModuleMetadata> = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
      .adapter(GradleModuleMetadata::class.java)
    val pomReader = MavenXpp3Reader()

    val commonVariantAttrs = arrayOf(
      Category.CATEGORY_ATTRIBUTE.name to Category.LIBRARY,
      LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name to LibraryElements.JAR,
      TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name to JavaVersion.current().majorVersion,
    )

    val shadowVariantAttrs = commonVariantAttrs + arrayOf(
      Bundling.BUNDLING_ATTRIBUTE.name to Bundling.SHADOWED,
      Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_RUNTIME,
    )

    fun MavenXpp3Reader.read(path: Path): Model = path.inputStream().use { read(it) }

    fun <T : Any> JsonAdapter<T>.fromJson(path: Path): T = requireNotNull(fromJson(path.readText()))
  }
}
