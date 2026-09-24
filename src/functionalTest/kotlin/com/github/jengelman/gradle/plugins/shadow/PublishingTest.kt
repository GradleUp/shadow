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
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_SOURCES_ELEMENTS_CONFIGURATION_NAME
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
      assertPomCommon("my/maven-all/1.0/maven-all-1.0.pom")
      assertShadowVariantCommon(
        "my/maven-all/1.0/maven-all-1.0.module",
        variantAttrs = variantAttrs,
      )
    }

    assertions(shadowVariantAttrs)

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
    assertions(shadowVariantAttrsWithoutTargetJvm + targetJvmAttr17)

    projectScript.appendText(
      """
      |java {
      |  targetCompatibility = JavaVersion.VERSION_11
      |}
      |"""
        .trimMargin()
    )
    assertions(shadowVariantAttrsWithoutTargetJvm + targetJvmAttr11)

    projectScript.appendText(
      """
      |java {
      |  sourceCompatibility = JavaVersion.VERSION_1_8
      |}
      |"""
        .trimMargin()
    )
    // sourceCompatibility doesn't affect the target JVM version.
    assertions(shadowVariantAttrsWithoutTargetJvm + targetJvmAttr11)

    projectScript.appendText(
      """
      |tasks.named('compileJava') {
      |  options.release = 8
      |}
      |"""
        .trimMargin()
    )
    // options.release flag is honored.
    assertions(shadowVariantAttrsWithoutTargetJvm + targetJvmAttr8)
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
      "my/maven-all/1.0/maven-all-1.0.module",
      variantAttrs = shadowVariantAttrsWithoutTargetJvm,
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
      "my/maven-all/1.0/maven-all-1.0.module",
      variantAttrs = shadowVariantAttrsWithoutTargetJvm,
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
      "my/maven-all/1.0/maven-all-1.0.module",
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
        projectBlock =
          """
          |$jarTask {
          |  enabled = false
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
        *withChecksums(
          "maven-1.0.jar",
          "maven-1.0.module",
          "maven-1.0.pom",
        )
      )
    assertShadowJarCommon("$artifactRoot/maven-1.0.jar")
    assertPomCommon("$artifactRoot/maven-1.0.pom")
    assertShadowVariantCommon("$artifactRoot/maven-1.0.module")
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
          |$jarTask {
          |  enabled = false
          |}
          |$sourcesJarTask {
          |  enabled = false
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
        *withChecksums(
          "maven-1.0.jar",
          "maven-1.0.module",
          "maven-1.0.pom",
          "maven-1.0-sources.jar",
        )
      )
    assertShadowJarCommon("$artifactRoot/maven-1.0.jar")
    assertPomCommon("$artifactRoot/maven-1.0.pom")
    repoGmm("$artifactRoot/maven-1.0.module").let { gmm ->
      assertShadowVariantCommon(gmm)
      assertShadowSourcesVariantCommon(gmm)
    }
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
    assertThat(repoPath(artifactRoot).entries)
      .containsOnly(
        *withChecksums(
          "maven-1.0-shaded.jar",
          "maven-1.0-shaded-sources.jar",
          "maven-1.0.module",
          "maven-1.0.pom",
        )
      )
    assertThat(
        repoGmm("$artifactRoot/maven-1.0.module").shadowSourcesElementsVariant.fileNames.single()
      )
      .isEqualTo("maven-1.0-shaded-sources.jar")
  }

  @Test
  fun dontPublishShadowJarAndSourcesWhenShadowJarDisabled() {
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
          |enabled = false
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
      .containsOnly(*withChecksums("maven-1.0.module", "maven-1.0.pom"))
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
    assertThat(repoPath(artifactRoot).entries)
      .containsOnly(
        *withChecksums(
          "maven-1.0.jar",
          "maven-1.0-sources.jar",
          "maven-1.0-all.jar",
          "maven-1.0-all-sources.jar",
          "maven-1.0.module",
          "maven-1.0.pom",
        )
      )
  }

  @Test
  fun publishShadowJarInsteadOfJarFromJavaComponent() {
    projectScript.appendText(
      publishConfiguration(
        projectBlock =
          """
          |java {
          |  withSourcesJar()
          |}
          |$sourcesJarTask {
          |  enabled = false
          |}
          |components.named('java', org.gradle.api.component.AdhocComponentWithVariants) {
          |  withVariantsFromConfiguration(configurations.runtimeElements) { skip() }
          |  withVariantsFromConfiguration(configurations.sourcesElements) { skip() }
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
          |  from components.java
          |}
          """
            .trimMargin(),
      )
    )

    publish()

    val artifactRoot = "my/maven/1.0"
    assertThat(repoPath(artifactRoot).entries)
      .containsOnly(
        *withChecksums(
          "maven-1.0.jar",
          "maven-1.0-sources.jar",
          "maven-1.0.module",
          "maven-1.0.pom",
        )
      )
    assertShadowJarCommon("$artifactRoot/maven-1.0.jar")
    assertThat(repoPom("$artifactRoot/maven-1.0.pom")).all {
      transform { it.dependencies.map(Dependency::coordinate) }.containsOnly("my:b:1.0")
      transform { it.dependencies.map { dep -> dep.scope to dep.isOptional } }
        .single()
        .isEqualTo("compile" to true)
    }
    repoGmm("$artifactRoot/maven-1.0.module").let { gmm ->
      assertThat(gmm.variantNames)
        .containsOnly(
          API_ELEMENTS_CONFIGURATION_NAME,
          SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
          SHADOW_SOURCES_ELEMENTS_CONFIGURATION_NAME,
        )
      assertShadowVariantCommon(gmm)
      assertShadowSourcesVariantCommon(gmm)
    }
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
          |$jarTask {
          |  enabled = false
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
    assertThat(repoPath(artifactRoot).entries)
      .containsOnly(
        *withChecksums(
          "maven-1.0.jar",
          "maven-1.0.module",
          "maven-1.0.pom",
        )
      )
    assertShadowJarCommon("$artifactRoot/maven-1.0.jar")
    assertPomCommon("$artifactRoot/maven-1.0.pom")
    repoGmm("$artifactRoot/maven-1.0.module").let { gmm ->
      assertShadowVariantCommon(gmm)
      assertThat(gmm.variantNames).containsOnly(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME)
    }
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
    assertThat(repoPath(artifactRoot).entries)
      .containsOnly(
        *withChecksums(
          "my-gradle-plugin-1.0.jar",
          "my-gradle-plugin-1.0-javadoc.jar",
          "my-gradle-plugin-1.0-sources.jar",
          "my-gradle-plugin-1.0.module",
          "my-gradle-plugin-1.0.pom",
        )
      )

    assertShadowJarCommon("$artifactRoot/my-gradle-plugin-1.0.jar")
    assertPomCommon("$artifactRoot/my-gradle-plugin-1.0.pom")
    assertShadowVariantCommon("$artifactRoot/my-gradle-plugin-1.0.module")
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
        *withChecksums(
          "my-artifact-2.0-my-classifier.my-ext",
          "my-artifact-2.0.module",
          "my-artifact-2.0.pom",
        )
      )

    assertShadowJarCommon("$artifactRoot/my-artifact-2.0-my-classifier.my-ext")
    assertPomCommon("$artifactRoot/my-artifact-2.0.pom")
    assertShadowVariantCommon("$artifactRoot/my-artifact-2.0.module")
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
        *withChecksums(
          "maven-1.0.jar",
          "maven-1.0.module",
          "maven-1.0.pom",
          "maven-1.0-all.jar",
        )
      )
    assertThat(repoPath("my/maven-all/1.0").entries)
      .containsOnly(
        *withChecksums(
          "maven-all-1.0-all.jar",
          "maven-all-1.0.module",
          "maven-all-1.0.pom",
        )
      )

    assertThat(repoJarPath("my/maven/1.0/maven-1.0.jar")).useAll {
      containsOnly("META-INF/", "META-INF/MANIFEST.MF")
    }
    assertThat(repoJarPath("my/maven/1.0/maven-1.0-all.jar")).useAll {
      containsOnly(*entriesInAB, "META-INF/", "META-INF/MANIFEST.MF")
    }

    assertPomCommon("my/maven/1.0/maven-1.0.pom", arrayOf("my:a:1.0", "my:b:1.0"))
    repoGmm("my/maven/1.0/maven-1.0.module").let { gmm ->
      // apiElements, runtimeElements, shadowRuntimeElements
      assertThat(gmm.variantNames)
        .containsOnly(
          API_ELEMENTS_CONFIGURATION_NAME,
          RUNTIME_ELEMENTS_CONFIGURATION_NAME,
          SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
        )
      assertJavaVariantsCommon(gmm, arrayOf("my:a:1.0", "my:b:1.0"))
      assertShadowVariantCommon(gmm)
    }

    assertPomCommon("my/maven-all/1.0/maven-all-1.0.pom")
    repoGmm("my/maven-all/1.0/maven-all-1.0.module").let { gmm ->
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
    val entriesCommon =
      withChecksums(
        "maven-1.0.jar",
        "maven-1.0.module",
        "maven-1.0.pom",
      )
    val artifactEntries = repoPath("my/maven/1.0/").entries
    val gmm = repoGmm("my/maven/1.0/maven-1.0.module")
    val pomDependencies =
      repoPom("my/maven/1.0/maven-1.0.pom").dependencies.map {
        it.coordinate to it.scope
      }

    if (addShadowVariant) {
      assertThat(artifactEntries)
        .containsOnly(
          *withChecksums("maven-1.0-all.jar"),
          *entriesCommon,
        )
      assertThat(gmm.variantNames)
        .containsOnly(
          API_ELEMENTS_CONFIGURATION_NAME,
          RUNTIME_ELEMENTS_CONFIGURATION_NAME,
          SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME,
        )
      assertJavaVariantsCommon(gmm)
      assertShadowVariantCommon(gmm)
      assertThat(pomDependencies).containsOnly("my:a:1.0" to "runtime", "my:b:1.0" to "compile")
    } else {
      assertThat(artifactEntries).containsOnly(*entriesCommon)
      assertThat(gmm.variantNames)
        .containsOnly(API_ELEMENTS_CONFIGURATION_NAME, RUNTIME_ELEMENTS_CONFIGURATION_NAME)
      assertJavaVariantsCommon(gmm)
      assertThat(pomDependencies).containsOnly("my:a:1.0" to "runtime")
    }
  }

  @Test
  fun publishKmpWithShadowedSources() {
    val stdlib = compileOnlyStdlib(true)
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
      |        $stdlib
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
    assertThat(repoPath(artifactRoot).entries)
      .containsOnly(
        *withChecksums(
          "my-all-1.0.jar",
          "my-all-1.0-sources.jar",
          "my-all-1.0.pom",
        )
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

    assertPomCommon("$artifactRoot/my-all-1.0.pom", emptyArray())
  }

  private fun repoPath(relative: String): Path {
    return remoteRepoPath.resolve(relative).also { check(it.exists()) { "Path not found: $it" } }
  }

  private fun repoJarPath(relative: String): JarPath {
    return JarPath(remoteRepoPath.resolve(relative))
  }

  private fun repoGmm(relative: String): GradleModuleMetadata {
    return gmmAdapter.fromJson(repoPath(relative))
  }

  private fun repoPom(relative: String): Model {
    return pomReader.read(repoPath(relative))
  }

  private fun publish(vararg arguments: String): BuildResult =
    runWithSuccess("build", "publish", *arguments)

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
           |${publishingBlock(projectBlock = projectBlock, publicationsBlock = publicationsBlock)}
           |// Place shadow jar block after publishing block to cover more lazy cases.
           |$shadowJarTask {
           |  $shadowBlock
           |}
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

  private fun assertPomCommon(relative: String, coordinates: Array<String> = arrayOf("my:b:1.0")) {
    assertThat(repoPom(relative)).all {
      transform { it.dependencies.map(Dependency::coordinate) }.containsOnly(*coordinates)
      if (coordinates.isNotEmpty()) {
        // All scopes should be runtime.
        transform { it.dependencies.map(Dependency::getScope).distinct() }
          .single()
          .isEqualTo("runtime")
      }
    }
  }

  private fun assertJavaVariantsCommon(
    gmm: GradleModuleMetadata,
    runtimeCoordinates: Array<String> = arrayOf("my:a:1.0"),
  ) {
    assertThat(gmm.apiElementsVariant).all {
      transform { it.attributes }.containsOnly(*apiVariantAttrs)
      transform { it.coordinates }.isEmpty()
    }
    assertThat(gmm.runtimeElementsVariant).all {
      transform { it.attributes }.containsOnly(*runtimeVariantAttrs)
      transform { it.coordinates }.containsOnly(*runtimeCoordinates)
    }
  }

  private fun assertShadowVariantCommon(
    gmm: Any,
    variantAttrs: Array<Pair<String, String>> = shadowVariantAttrs,
    coordinates: Array<String> = arrayOf("my:b:1.0"),
    body: Assert<GradleModuleMetadata.Variant>.() -> Unit = {},
  ) {
    val realGmm =
      when (gmm) {
        is String -> repoGmm(gmm)
        is GradleModuleMetadata -> gmm
        else -> error("Unsupported type $gmm")
      }
    assertThat(realGmm.shadowRuntimeElementsVariant).all {
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

  private fun assertShadowJarCommon(relative: String) {
    assertThat(repoJarPath(relative)).useAll {
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

    val apiVariantAttrs =
      commonVariantAttrs +
        arrayOf(
          Bundling.BUNDLING_ATTRIBUTE.name to Bundling.EXTERNAL,
          Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_API,
        )

    val runtimeVariantAttrs =
      commonVariantAttrs +
        arrayOf(
          Bundling.BUNDLING_ATTRIBUTE.name to Bundling.EXTERNAL,
          Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_RUNTIME,
        )

    val shadowVariantAttrs =
      commonVariantAttrs +
        arrayOf(
          Bundling.BUNDLING_ATTRIBUTE.name to Bundling.SHADOWED,
          Usage.USAGE_ATTRIBUTE.name to Usage.JAVA_RUNTIME,
        )

    val shadowVariantAttrsWithoutTargetJvm =
      shadowVariantAttrs
        .filterNot { (name, _) -> name == TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name }
        .toTypedArray()

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

    fun withChecksums(vararg baseNames: String): Array<String> =
      baseNames
        .flatMap { listOf(it, "$it.md5", "$it.sha1", "$it.sha256", "$it.sha512") }
        .toTypedArray()
  }
}
