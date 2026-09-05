package com.github.jengelman.gradle.plugins.shadow

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.single
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsNone
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.GradleModuleMetadata
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import com.github.jengelman.gradle.plugins.shadow.util.coordinate
import com.github.jengelman.gradle.plugins.shadow.util.prependText
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
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.gradle.api.JavaVersion
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PublishingTest : BasePluginTest() {
  @TempDir lateinit var remoteRepoPath: Path

  @BeforeEach
  override fun beforeEach() {
    super.beforeEach()
    settingsScript.appendText("rootProject.name = 'maven'\n")
  }

  @Test
  fun publishShadowJarWithCorrectTargetJvm() {
    projectScript.appendText(
      publishConfiguration(
        shadowBlock =
          """
          |archiveClassifier = ''
          |archiveBaseName = 'maven-all'
          """
            .trimMargin()
      )
    )

    val assertions = { variantAttrs: Array<Pair<String, String>> ->
      publish()
      assertPomCommon(repoPath("my/maven-all/1.0/maven-all-1.0.pom"))
      val gmm = gmmAdapter.fromJson(repoPath("my/maven-all/1.0/maven-all-1.0.module"))
      assertShadowVariantCommon(gmm, variantAttrs = variantAttrs)
    }

    assertions(shadowVariantAttrs)

    val attrsWithoutTargetJvm =
      shadowVariantAttrs
        .filterNot { (name, _) -> name == TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name }
        .toTypedArray()
    val targetJvmAttr17 = TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name to "17"
    val targetJvmAttr11 = TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name to "11"
    val targetJvmAttr8 = TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name to "8"

    settingsScript.prependText(
      """
      |plugins {
      |  id 'org.gradle.toolchains.foojay-resolver-convention'
      |}
      |"""
        .trimMargin()
    )
    projectScript.appendText(
      """
      |java {
      |  toolchain.languageVersion = JavaLanguageVersion.of(17)
      |}
      |"""
        .trimMargin()
    )
    assertions(attrsWithoutTargetJvm + targetJvmAttr17)

    projectScript.appendText(
      """
      |java {
      |  targetCompatibility = JavaVersion.VERSION_11
      |}
      |"""
        .trimMargin()
    )
    assertions(attrsWithoutTargetJvm + targetJvmAttr11)

    projectScript.appendText(
      """
      |java {
      |  sourceCompatibility = JavaVersion.VERSION_1_8
      |}
      |"""
        .trimMargin()
    )
    // sourceCompatibility doesn't affect the target JVM version.
    assertions(attrsWithoutTargetJvm + targetJvmAttr11)

    projectScript.appendText(
      """
      |tasks.named('compileJava') {
      |  options.release = 8
      |}
      |"""
        .trimMargin()
    )
    // options.release flag is honored.
    assertions(attrsWithoutTargetJvm + targetJvmAttr8)
  }

  @Test // #1665
  fun dontInjectTargetJvmVersionWhenAutoTargetJvmDisabled() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |java {
          |  disableAutoTargetJvm()
          |}
          """
            .trimMargin(),
        shadowBlock =
          """
          |archiveClassifier = ''
          |archiveBaseName = 'maven-all'
          """
            .trimMargin(),
      )
    )

    val result = publish(infoArgument)

    assertThat(result.output)
      .contains(
        "Cannot set the target JVM version to Int.MAX_VALUE when `java.autoTargetJvmDisabled` is enabled or in other cases."
      )
    assertShadowVariantCommon(
      gmm = gmmAdapter.fromJson(repoPath("my/maven-all/1.0/maven-all-1.0.module")),
      variantAttrs =
        shadowVariantAttrs
          .filterNot { (name, _) -> name == TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name }
          .toTypedArray(),
    )
  }

  @Test
  fun dontInjectTargetJvmVersionWhenOptingOut() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |shadow {
          |  addTargetJvmVersionAttribute = false
          |}
          """
            .trimMargin(),
        shadowBlock =
          """
          |archiveClassifier = ''
          |archiveBaseName = 'maven-all'
          """
            .trimMargin(),
      )
    )

    val result = publish(infoArgument)

    assertThat(result.output)
      .contains(
        "Skipping setting org.gradle.jvm.version attribute for shadowRuntimeElements configuration."
      )
    assertShadowVariantCommon(
      gmm = gmmAdapter.fromJson(repoPath("my/maven-all/1.0/maven-all-1.0.module")),
      variantAttrs =
        shadowVariantAttrs
          .filterNot { (name, _) -> name == TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name }
          .toTypedArray(),
    )
  }

  @Test
  fun overrideBundlingAttrInGradleMetadata() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |shadow {
          |  bundlingAttribute = Bundling.EMBEDDED
          |}
          """
            .trimMargin(),
        shadowBlock =
          """
          |archiveClassifier = ''
          |archiveBaseName = 'maven-all'
          """
            .trimMargin(),
      )
    )

    publish()

    assertShadowVariantCommon(
      gmm = gmmAdapter.fromJson(repoPath("my/maven-all/1.0/maven-all-1.0.module")),
      variantAttrs =
        commonVariantAttrs +
          arrayOf(
            Bundling.BUNDLING_ATTRIBUTE.name to Bundling.EMBEDDED,
            Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_RUNTIME,
          ),
    )
  }

  @Test
  fun publishShadowJarInsteadOfJar() {
    projectScript.appendText(
      publishConfiguration(
        shadowBlock =
          """
          |archiveClassifier = ''
          """
            .trimMargin(),
        publicationsBlock =
          """
          |shadow(MavenPublication) {
          |  from components.shadow
          |}
          """
            .trimMargin(),
      )
    )

    publish()

    val artifactRoot = "my/maven/1.0"
    assertThat(repoPath(artifactRoot).entries)
      .containsOnly(
        "maven-1.0.jar",
        "maven-1.0.module",
        "maven-1.0.pom",
        "maven-1.0.jar.md5",
        "maven-1.0.module.md5",
        "maven-1.0.pom.md5",
        "maven-1.0.jar.sha1",
        "maven-1.0.module.sha1",
        "maven-1.0.pom.sha1",
        "maven-1.0.jar.sha256",
        "maven-1.0.module.sha256",
        "maven-1.0.pom.sha256",
        "maven-1.0.jar.sha512",
        "maven-1.0.module.sha512",
        "maven-1.0.pom.sha512",
      )
    assertShadowJarCommon(repoJarPath("$artifactRoot/maven-1.0.jar"))
    assertPomCommon(repoPath("$artifactRoot/maven-1.0.pom"))
    val gmm = gmmAdapter.fromJson(repoPath("$artifactRoot/maven-1.0.module"))
    assertShadowVariantCommon(gmm)
  }

  @Test
  fun publishShadowJarWithSourcesWhenWithSourcesJarEnabled() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |java {
          |  withSourcesJar()
          |}
          """
            .trimMargin(),
        shadowBlock =
          """
          |archiveClassifier = ''
          """
            .trimMargin(),
        publicationsBlock =
          """
          |shadow(MavenPublication) {
          |  from components.shadow
          |}
          """
            .trimMargin(),
      )
    )

    publish()

    val artifactRoot = "my/maven/1.0"
    assertThat(repoPath(artifactRoot).entries)
      .containsOnly(
        "maven-1.0.jar",
        "maven-1.0.module",
        "maven-1.0.pom",
        "maven-1.0.jar.md5",
        "maven-1.0.module.md5",
        "maven-1.0.pom.md5",
        "maven-1.0.jar.sha1",
        "maven-1.0.module.sha1",
        "maven-1.0.pom.sha1",
        "maven-1.0.jar.sha256",
        "maven-1.0.module.sha256",
        "maven-1.0.pom.sha256",
        "maven-1.0.jar.sha512",
        "maven-1.0.module.sha512",
        "maven-1.0.pom.sha512",
        "maven-1.0-sources.jar",
        "maven-1.0-sources.jar.md5",
        "maven-1.0-sources.jar.sha1",
        "maven-1.0-sources.jar.sha256",
        "maven-1.0-sources.jar.sha512",
      )
    assertShadowJarCommon(repoJarPath("$artifactRoot/maven-1.0.jar"))
    assertPomCommon(repoPath("$artifactRoot/maven-1.0.pom"))
    val gmm = gmmAdapter.fromJson(repoPath("$artifactRoot/maven-1.0.module"))
    assertShadowVariantCommon(gmm)
    assertShadowSourcesVariantCommon(gmm)
  }

  @Test
  fun publishWithSourcesJarAndCustomClassifier() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |java {
          |  withSourcesJar()
          |}
          """
            .trimMargin(),
        shadowBlock =
          """
          |archiveClassifier = 'shaded'
          |archiveSourcesFile = layout.buildDirectory.file('custom.jar')
          """
            .trimMargin(),
        publicationsBlock =
          """
          |shadow(MavenPublication) {
          |  from components.shadow
          |}
          """
            .trimMargin(),
      )
    )

    publish()

    val artifactRoot = "my/maven/1.0"
    assertThat(repoPath(artifactRoot).entries.filter { it.endsWith(".jar") })
      .containsOnly(
        "maven-1.0-shaded.jar",
        "maven-1.0-shaded-sources.jar",
      )
    val gmm = gmmAdapter.fromJson(repoPath("$artifactRoot/maven-1.0.module"))
    assertThat(gmm.shadowSourcesElementsVariant.fileNames.single())
      .isEqualTo("maven-1.0-shaded-sources.jar")
  }

  @Test
  fun publishWithSourcesJarAndCustomClassifierAfterPublishingBlock() {
    projectScript.appendText(
      """
      |apply plugin: 'maven-publish'
      |java {
      |  withSourcesJar()
      |}
      |publishing {
      |  repositories {
      |    maven { url = '${remoteRepoPath.toUri()}' }
      |  }
      |  publications {
      |    shadow(MavenPublication) {
      |      from components.shadow
      |    }
      |  }
      |}
      |tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      |  archiveClassifier = 'shaded'
      |  archiveSourcesFile = layout.buildDirectory.file('custom.jar')
      |}
      """
        .trimMargin()
    )

    publish()

    val artifactRoot = "my/maven/1.0"
    assertThat(repoPath(artifactRoot).entries.filter { it.endsWith(".jar") })
      .containsOnly(
        "maven-1.0-shaded.jar",
        "maven-1.0-shaded-sources.jar",
      )
    val gmm = gmmAdapter.fromJson(repoPath("$artifactRoot/maven-1.0.module"))
    assertThat(gmm.shadowSourcesElementsVariant.fileNames.single())
      .isEqualTo("maven-1.0-shaded-sources.jar")
  }

  @Test
  fun publishJavaComponentWithShadowAndSourcesVariants() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |java {
          |  withSourcesJar()
          |}
          """
            .trimMargin(),
        publicationsBlock =
          """
          |shadow(MavenPublication) {
          |  from components.java
          |}
          """
            .trimMargin(),
      )
    )

    publish()

    val artifactRoot = "my/maven/1.0"
    assertThat(repoPath(artifactRoot).entries.filter { it.endsWith(".jar") })
      .containsOnly(
        "maven-1.0.jar",
        "maven-1.0-sources.jar",
        "maven-1.0-all.jar",
        "maven-1.0-all-sources.jar",
      )
  }

  @Test
  fun dontPublishSourcesWhenGenerateSourcesJarDisabled() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |java {
          |  withSourcesJar()
          |}
          """
            .trimMargin(),
        shadowBlock =
          """
          |archiveClassifier = ''
          |generateSourcesJar = false
          """
            .trimMargin(),
        publicationsBlock =
          """
          |shadow(MavenPublication) {
          |  from components.shadow
          |}
          """
            .trimMargin(),
      )
    )

    val result = publish(infoArgument)

    assertThat(result.output)
      .contains("Skipping adding shadowSourcesElements variant to shadow component.")
    val artifactRoot = "my/maven/1.0"
    assertThat(repoPath(artifactRoot).entries.filter { it.contains("sources") }).isEmpty()
    assertShadowJarCommon(repoJarPath("$artifactRoot/maven-1.0.jar"))
    assertPomCommon(repoPath("$artifactRoot/maven-1.0.pom"))
    val gmm = gmmAdapter.fromJson(repoPath("$artifactRoot/maven-1.0.module"))
    assertShadowVariantCommon(gmm)
    assertThat(gmm.variantNames).containsOnly(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME)
  }

  @Test
  fun publishCustomShadowJar() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |def testShadowJar = tasks.register('testShadowJar', ${ShadowJar::class.java.name}) {
          |  description = 'Create a combined JAR of project and test dependencies'
          |  archiveClassifier = 'tests'
          |  from sourceSets.named('test').map { it.output }
          |  configurations = project.configurations.named('testRuntimeClasspath').map { [it] }
          |}
          """
            .trimMargin(),
        dependenciesBlock =
          """
          |testImplementation 'junit:junit:3.8.2'
          """
            .trimMargin(),
        publicationsBlock =
          """
          |shadow(MavenPublication) {
          |  artifact testShadowJar
          |}
          """
            .trimMargin(),
      )
    )

    publish()

    assertThat(repoJarPath("my/maven/1.0/maven-1.0-tests.jar")).useAll {
      containsOnly(*junitEntries, "META-INF/", "META-INF/MANIFEST.MF")
    }
  }

  @Test
  fun publishShadowedGradlePlugin() {
    writeGradlePluginModule()
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |apply plugin: 'com.gradle.plugin-publish'
          |group = 'my.plugin'
          |version = '1.0'
          """
            .trimMargin(),
        shadowBlock =
          """
          |archiveClassifier = ''
          """
            .trimMargin(),
        publicationsBlock =
          """
          |pluginMaven(MavenPublication) {
          |  artifactId = 'my-gradle-plugin'
          |}
          """
            .trimMargin(),
      )
    )

    publish()

    val artifactRoot = "my/plugin/my-gradle-plugin/1.0"
    assertThat(repoPath(artifactRoot).entries.filter { it.endsWith(".jar") })
      .containsOnly(
        "my-gradle-plugin-1.0.jar",
        "my-gradle-plugin-1.0-javadoc.jar",
        "my-gradle-plugin-1.0-sources.jar",
      )

    assertShadowJarCommon(repoJarPath("$artifactRoot/my-gradle-plugin-1.0.jar"))
    assertPomCommon(repoPath("$artifactRoot/my-gradle-plugin-1.0.pom"))
    assertShadowVariantCommon(
      gmmAdapter.fromJson(repoPath("$artifactRoot/my-gradle-plugin-1.0.module"))
    )
  }

  @Test // #614, #860, #945
  fun publishShadowJarWithCustomArtifactName() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |group = 'my-group'
          |version = '2.0'
          """
            .trimMargin(),
        shadowBlock =
          """
          |archiveClassifier = 'my-classifier'
          |archiveExtension = 'my-ext'
          |archiveBaseName = 'maven-all'
          """
            .trimMargin(),
        publicationsBlock =
          """
          |shadow(MavenPublication) {
          |  from components.shadow
          |  artifactId = 'my-artifact'
          |}
          """
            .trimMargin(),
      )
    )

    publish()

    val artifactRoot = "my-group/my-artifact/2.0"
    assertThat(repoPath(artifactRoot).entries)
      .containsOnly(
        "my-artifact-2.0-my-classifier.my-ext.sha512",
        "my-artifact-2.0-my-classifier.my-ext",
        "my-artifact-2.0.pom.sha256",
        "my-artifact-2.0.module",
        "my-artifact-2.0.pom",
        "my-artifact-2.0.module.sha256",
        "my-artifact-2.0.module.sha1",
        "my-artifact-2.0.module.md5",
        "my-artifact-2.0.pom.sha512",
        "my-artifact-2.0-my-classifier.my-ext.sha256",
        "my-artifact-2.0.module.sha512",
        "my-artifact-2.0-my-classifier.my-ext.sha1",
        "my-artifact-2.0-my-classifier.my-ext.md5",
        "my-artifact-2.0.pom.md5",
        "my-artifact-2.0.pom.sha1",
      )

    assertShadowJarCommon(repoJarPath("$artifactRoot/my-artifact-2.0-my-classifier.my-ext"))
    assertPomCommon(repoPath("$artifactRoot/my-artifact-2.0.pom"))
    assertShadowVariantCommon(gmmAdapter.fromJson(repoPath("$artifactRoot/my-artifact-2.0.module")))
  }

  @Test
  fun publishJarAndShadowJarWithGradleMetadata() {
    projectScript.appendText(
      publishConfiguration(
        dependenciesBlock =
          """
          |implementation 'my:a:1.0'
          |implementation 'my:b:1.0'
          |shadow 'my:b:1.0'
          """
            .trimMargin(),
        publicationsBlock =
          """
          |java(MavenPublication) {
          |  from components.java
          |}
          |shadow(MavenPublication) {
          |  from components.shadow
          |  artifactId = "maven-all"
          |}
          """
            .trimMargin(),
      )
    )

    publish()

    assertThat(repoPath("my/maven/1.0").entries)
      .containsOnly(
        // Entries of maven-1.0.jar
        "maven-1.0.jar",
        "maven-1.0.module",
        "maven-1.0.pom",
        "maven-1.0.jar.md5",
        "maven-1.0.module.md5",
        "maven-1.0.pom.md5",
        "maven-1.0.jar.sha1",
        "maven-1.0.module.sha1",
        "maven-1.0.pom.sha1",
        "maven-1.0.jar.sha256",
        "maven-1.0.module.sha256",
        "maven-1.0.pom.sha256",
        "maven-1.0.jar.sha512",
        "maven-1.0.module.sha512",
        "maven-1.0.pom.sha512",
        // Entries of maven-1.0-all.jar
        "maven-1.0-all.jar",
        "maven-1.0-all.jar.md5",
        "maven-1.0-all.jar.sha1",
        "maven-1.0-all.jar.sha256",
        "maven-1.0-all.jar.sha512",
      )
    assertThat(repoPath("my/maven-all/1.0").entries)
      .containsOnly(
        "maven-all-1.0-all.jar",
        "maven-all-1.0.module",
        "maven-all-1.0.pom",
        "maven-all-1.0-all.jar.md5",
        "maven-all-1.0.module.md5",
        "maven-all-1.0.pom.md5",
        "maven-all-1.0-all.jar.sha1",
        "maven-all-1.0.module.sha1",
        "maven-all-1.0.pom.sha1",
        "maven-all-1.0-all.jar.sha256",
        "maven-all-1.0.module.sha256",
        "maven-all-1.0.pom.sha256",
        "maven-all-1.0-all.jar.sha512",
        "maven-all-1.0.module.sha512",
        "maven-all-1.0.pom.sha512",
      )

    assertThat(repoJarPath("my/maven/1.0/maven-1.0.jar")).useAll {
      containsOnly("META-INF/", "META-INF/MANIFEST.MF")
    }
    assertThat(repoJarPath("my/maven/1.0/maven-1.0-all.jar")).useAll {
      containsOnly(*entriesInAB, "META-INF/", "META-INF/MANIFEST.MF")
    }

    assertPomCommon(repoPath("my/maven/1.0/maven-1.0.pom"), arrayOf("my:a:1.0", "my:b:1.0"))
    gmmAdapter.fromJson(repoPath("my/maven/1.0/maven-1.0.module")).let { gmm ->
      // apiElements, runtimeElements, shadowRuntimeElements
      assertThat(gmm.variantNames)
        .containsOnly(
          API_ELEMENTS_CONFIGURATION_NAME,
          RUNTIME_ELEMENTS_CONFIGURATION_NAME,
          SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
        )
      assertThat(gmm.apiElementsVariant).all {
        transform { it.attributes }
          .containsOnly(
            *commonVariantAttrs,
            Bundling.BUNDLING_ATTRIBUTE.name to Bundling.EXTERNAL,
            Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_API,
          )
        transform { it.coordinates }.isEmpty()
      }
      assertThat(gmm.runtimeElementsVariant).all {
        transform { it.attributes }
          .containsOnly(
            *commonVariantAttrs,
            Bundling.BUNDLING_ATTRIBUTE.name to Bundling.EXTERNAL,
            Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_RUNTIME,
          )
        transform { it.coordinates }.containsOnly("my:a:1.0", "my:b:1.0")
      }
      assertShadowVariantCommon(gmm)
    }

    assertPomCommon(repoPath("my/maven-all/1.0/maven-all-1.0.pom"))
    gmmAdapter.fromJson(repoPath("my/maven-all/1.0/maven-all-1.0.module")).let { gmm ->
      assertThat(gmm.variantNames).containsOnly(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME)
      assertShadowVariantCommon(gmm)
    }
  }

  @ParameterizedTest // #651
  @ValueSource(booleans = [false, true])
  fun publishShadowVariantJar(addShadowVariant: Boolean) {
    projectScript.appendText(
      publishingBlock(
        projectBlock =
          """
          |dependencies {
          |  implementation 'my:a:1.0'
          |  shadow 'my:b:1.0'
          |}
          |shadow {
          |  addShadowVariantIntoJavaComponent = $addShadowVariant
          |}
          """
            .trimMargin(),
        publicationsBlock =
          """
          |shadow(MavenPublication) {
          |  from components.java
          |}
          """
            .trimMargin(),
      )
    )

    val result = publish(infoArgument)

    assertThat(result.output)
      .contains(
        if (addShadowVariant) {
          "Adding shadowRuntimeElements variant to java component."
        } else {
          "Skipping adding shadowRuntimeElements variant to java component."
        }
      )
    val assertVariantsCommon = { gmm: GradleModuleMetadata ->
      assertThat(gmm.apiElementsVariant).all {
        transform { it.attributes }
          .containsOnly(
            *commonVariantAttrs,
            Bundling.BUNDLING_ATTRIBUTE.name to Bundling.EXTERNAL,
            Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_API,
          )
        transform { it.coordinates }.isEmpty()
      }
      assertThat(gmm.runtimeElementsVariant).all {
        transform { it.attributes }
          .containsOnly(
            *commonVariantAttrs,
            Bundling.BUNDLING_ATTRIBUTE.name to Bundling.EXTERNAL,
            Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_RUNTIME,
          )
        transform { it.coordinates }.containsOnly("my:a:1.0")
      }
    }
    val entriesCommon =
      arrayOf(
        "maven-1.0.jar",
        "maven-1.0.jar.md5",
        "maven-1.0.jar.sha1",
        "maven-1.0.jar.sha256",
        "maven-1.0.jar.sha512",
        "maven-1.0.module",
        "maven-1.0.module.md5",
        "maven-1.0.module.sha1",
        "maven-1.0.module.sha256",
        "maven-1.0.module.sha512",
        "maven-1.0.pom",
        "maven-1.0.pom.md5",
        "maven-1.0.pom.sha1",
        "maven-1.0.pom.sha256",
        "maven-1.0.pom.sha512",
      )
    val artifactEntries = repoPath("my/maven/1.0/").entries
    val gmm = gmmAdapter.fromJson(repoPath("my/maven/1.0/maven-1.0.module"))
    val pomDependencies =
      pomReader.read(repoPath("my/maven/1.0/maven-1.0.pom")).dependencies.map {
        it.coordinate to it.scope
      }

    if (addShadowVariant) {
      assertThat(artifactEntries)
        .containsOnly(
          "maven-1.0-all.jar",
          "maven-1.0-all.jar.md5",
          "maven-1.0-all.jar.sha1",
          "maven-1.0-all.jar.sha256",
          "maven-1.0-all.jar.sha512",
          *entriesCommon,
        )
      assertThat(gmm.variantNames)
        .containsOnly(
          API_ELEMENTS_CONFIGURATION_NAME,
          RUNTIME_ELEMENTS_CONFIGURATION_NAME,
          SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
        )
      assertVariantsCommon(gmm)
      assertShadowVariantCommon(gmm)
      assertThat(pomDependencies).containsOnly("my:a:1.0" to "runtime", "my:b:1.0" to "compile")
    } else {
      assertThat(artifactEntries).containsOnly(*entriesCommon)
      assertThat(gmm.variantNames)
        .containsOnly(API_ELEMENTS_CONFIGURATION_NAME, RUNTIME_ELEMENTS_CONFIGURATION_NAME)
      assertVariantsCommon(gmm)
      assertThat(pomDependencies).containsOnly("my:a:1.0" to "runtime")
    }
  }

  @Test
  fun publishKmpWithShadowedSources() {
    path("gradle.properties").writeText("kotlin.stdlib.default.dependency=false")
    projectScript.writeText(
      """
      |plugins {
      |  id 'org.jetbrains.kotlin.multiplatform'
      |  id 'com.gradleup.shadow'
      |  id 'maven-publish'
      |}
      |group = 'my'
      |version = '1.0'
      |kotlin {
      |  jvm()
      |  sourceSets {
      |    commonMain {
      |      dependencies {
      |        implementation 'my:g:1.0'
      |        compileOnly 'org.jetbrains.kotlin:kotlin-stdlib'
      |      }
      |    }
      |    jvmMain {
      |      dependencies {
      |        implementation 'my:h:1.0'
      |      }
      |    }
      |  }
      |}
      |$shadowJarTask {
      |  archiveClassifier = ''
      |  generateSourcesJar = true
      |}
      |publishing {
      |  repositories {
      |    maven { url = '${remoteRepoPath.toUri()}' }
      |  }
      |  publications {
      |    shadow(MavenPublication) {
      |      artifactId = 'my-all'
      |      artifact($shadowJarTask)
      |      artifact($shadowJarTask.flatMap { it.archiveSourcesFile }) {
      |        classifier = 'sources'
      |      }
      |    }
      |  }
      |}
      """
        .trimMargin()
    )
    writeClass(sourceSet = "commonMain", jvmLang = JvmLang.Kotlin, className = "CommonMain")
    writeClass(sourceSet = "jvmMain", jvmLang = JvmLang.Kotlin, className = "JvmMain")

    publish()

    val artifactRoot = "my/my-all/1.0"
    assertThat(repoPath(artifactRoot).entries.filter { it.endsWith(".jar") })
      .containsOnly(
        "my-all-1.0.jar",
        "my-all-1.0-sources.jar",
      )

    assertThat(repoJarPath("$artifactRoot/my-all-1.0.jar")).useAll {
      containsOnly(
        "my/",
        "g/",
        "h/",
        "my/CommonMain.class",
        "my/JvmMain.class",
        "g/G.class",
        "h/H.class",
        "h/UnusedH.class",
        "META-INF/my_maven.kotlin_module",
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }

    assertThat(repoJarPath("$artifactRoot/my-all-1.0-sources.jar")).useAll {
      containsOnly(
        "my/",
        "g/",
        "h/",
        "my/CommonMain.kt",
        "my/JvmMain.kt",
        "g/G.java",
        "h/H.java",
        "h/UnusedH.java",
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }

    assertPomCommon(repoPath("$artifactRoot/my-all-1.0.pom"), emptyArray())
    assertThat(repoPath(artifactRoot).entries.filter { it.endsWith(".module") }).isEmpty()
  }

  private fun repoPath(relative: String): Path {
    return remoteRepoPath.resolve(relative).also { check(it.exists()) { "Path not found: $it" } }
  }

  private fun repoJarPath(relative: String): JarPath {
    return JarPath(remoteRepoPath.resolve(relative))
  }

  private fun publish(vararg arguments: String): BuildResult = runWithSuccess("publish", *arguments)

  private fun publishConfiguration(
    projectBlock: String = "",
    dependenciesBlock: String =
      """
      |implementation 'my:a:1.0'
      |shadow 'my:b:1.0'
      """
        .trimMargin(),
    shadowBlock: String = "",
    publicationsBlock: String =
      """
      |shadow(MavenPublication) {
      |  from components.shadow
      |  artifactId = 'maven-all'
      |}
      """
        .trimMargin(),
  ): String {
    return """
           |dependencies {
           |  $dependenciesBlock
           |}
           |$shadowJarTask {
           |  $shadowBlock
           |}
           |${publishingBlock(projectBlock = projectBlock, publicationsBlock = publicationsBlock)}
           |
           """
      .trimMargin()
  }

  private fun publishingBlock(projectBlock: String, publicationsBlock: String): String {
    return """
           |apply plugin: 'maven-publish'
           |$projectBlock
           |publishing {
           |  publications {
           |    $publicationsBlock
           |  }
           |  repositories {
           |    maven { url = '${remoteRepoPath.toUri()}' }
           |  }
           |}
           """
      .trimMargin()
  }

  private fun assertPomCommon(pomPath: Path, coordinates: Array<String> = arrayOf("my:b:1.0")) {
    assertThat(pomReader.read(pomPath)).all {
      transform { it.dependencies.map(Dependency::coordinate) }.containsOnly(*coordinates)
      if (coordinates.isNotEmpty()) {
        // All scopes should be runtime.
        transform { it.dependencies.map(Dependency::getScope).distinct() }
          .single()
          .isEqualTo("runtime")
      }
    }
  }

  private fun assertShadowVariantCommon(
    gmm: GradleModuleMetadata,
    variantAttrs: Array<Pair<String, String>> = shadowVariantAttrs,
    coordinates: Array<String> = arrayOf("my:b:1.0"),
    body: Assert<GradleModuleMetadata.Variant>.() -> Unit = {},
  ) {
    assertThat(gmm.shadowRuntimeElementsVariant).all {
      transform { it.attributes }.containsOnly(*variantAttrs)
      transform { it.coordinates }.containsOnly(*coordinates)
      body()
    }
  }

  private fun assertShadowSourcesVariantCommon(
    gmm: GradleModuleMetadata,
    variantAttrs: Array<Pair<String, String>> = shadowSourcesVariantAttrs,
    body: Assert<GradleModuleMetadata.Variant>.() -> Unit = {},
  ) {
    assertThat(gmm.shadowSourcesElementsVariant).all {
      transform { it.attributes }.containsOnly(*variantAttrs)
      body()
    }
  }

  private fun assertShadowJarCommon(jarPath: JarPath) {
    assertThat(jarPath).useAll {
      containsAtLeast(*entriesInA)
      containsNone(*entriesInB)
      getMainAttr(classPathAttributeKey).isEqualTo("b-1.0.jar")
    }
  }

  private companion object {
    val gmmAdapter: JsonAdapter<GradleModuleMetadata> =
      Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(GradleModuleMetadata::class.java)
    val pomReader = MavenXpp3Reader()

    val commonVariantAttrs =
      arrayOf(
        Category.CATEGORY_ATTRIBUTE.name to Category.LIBRARY,
        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name to LibraryElements.JAR,
        TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name to JavaVersion.current().majorVersion,
      )

    val shadowVariantAttrs =
      commonVariantAttrs +
        arrayOf(
          Bundling.BUNDLING_ATTRIBUTE.name to Bundling.SHADOWED,
          Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_RUNTIME,
        )

    val shadowSourcesVariantAttrs =
      arrayOf(
        Category.CATEGORY_ATTRIBUTE.name to Category.DOCUMENTATION,
        Bundling.BUNDLING_ATTRIBUTE.name to Bundling.SHADOWED,
        DocsType.DOCS_TYPE_ATTRIBUTE.name to DocsType.SOURCES,
        Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_RUNTIME,
      )

    fun MavenXpp3Reader.read(path: Path): Model = path.inputStream().use { read(it) }

    fun <T : Any> JsonAdapter<T>.fromJson(path: Path): T = checkNotNull(fromJson(path.readText()))

    val Path.entries: List<String>
      get() = listDirectoryEntries().map { it.name }
  }
}
