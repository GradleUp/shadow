package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsNone
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.installShadowDist
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.runShadow
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.shadowDistZip
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.startShadowScripts
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.internal.applicationExtension
import com.github.jengelman.gradle.plugins.shadow.internal.javaPluginExtension
import com.github.jengelman.gradle.plugins.shadow.internal.javaToolchainService
import com.github.jengelman.gradle.plugins.shadow.internal.runtimeConfiguration
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ShadowPropertiesTest {
  private lateinit var project: Project

  @BeforeEach
  fun beforeEach() {
    project = ProjectBuilder.builder().withName(PROJECT_NAME).build().also {
      it.version = VERSION
      it.plugins.apply(ShadowPlugin::class.java)
    }
  }

  @Test
  fun misc() = with(project) {
    assertThat(plugins.hasPlugin(ShadowPlugin::class.java)).isTrue()
    assertThat(plugins.hasPlugin(LegacyShadowPlugin::class.java)).isTrue()
    assertThat(tasks.findByName(SHADOW_JAR_TASK_NAME)).isNull()

    with(extensions.getByType(ShadowExtension::class.java)) {
      assertThat(addShadowVariantIntoJavaComponent.get()).isTrue()
      assertThat(addTargetJvmVersionAttribute.get()).isTrue()
    }
  }

  @Test
  fun applyJavaPlugin() = with(project) {
    plugins.apply(JavaPlugin::class.java)
    val shadowJarTask = tasks.shadowJar.get()
    val shadowConfig = configurations.shadow.get()
    val assembleTask = tasks.getByName(ASSEMBLE_TASK_NAME)

    assertThat(shadowConfig.artifacts.files).contains(shadowJarTask.archiveFile.get().asFile)
    assertThat(assembleTask.dependsOnTaskNames).all {
      isNotEmpty()
      contains(shadowJarTask.name)
    }

    // Check inherited properties.
    with(shadowJarTask as Jar) {
      assertThat(group).isEqualTo(LifecycleBasePlugin.BUILD_GROUP)
      assertThat(description).isEqualTo("Create a combined JAR of project and runtime dependencies")

      assertThat(archiveAppendix.orNull).isNull()
      assertThat(archiveBaseName.get()).isEqualTo(PROJECT_NAME)
      assertThat(archiveClassifier.get()).isEqualTo("all")
      assertThat(archiveExtension.get()).isEqualTo("jar")
      assertThat(archiveFileName.get()).isEqualTo("my-project-1.0.0-all.jar")
      assertThat(archiveVersion.get()).isEqualTo(version)
      assertThat(archiveFile.get().asFile).all {
        isEqualTo(destinationDirectory.file(archiveFileName).get().asFile)
        isEqualTo(projectDir.resolve("build/libs/my-project-1.0.0-all.jar"))
      }
      assertThat(destinationDirectory.get().asFile)
        .isEqualTo(layout.buildDirectory.dir("libs").get().asFile)

      assertThat(duplicatesStrategy).isEqualTo(DuplicatesStrategy.EXCLUDE)
    }

    // Check self properties.
    with(shadowJarTask) {
      assertThat(addMultiReleaseAttribute.get()).isTrue()
      assertThat(enableAutoRelocation.get()).isFalse()
      assertThat(failOnDuplicateEntries.get()).isFalse()
      assertThat(minimizeJar.get()).isFalse()
      assertThat(mainClass.orNull).isNull()

      assertThat(relocationPrefix.get()).isEqualTo(ShadowBasePlugin.SHADOW)
      assertThat(configurations.get()).all {
        isNotEmpty()
        containsOnly(runtimeConfiguration)
      }
    }
  }

  @Test
  fun applyApplicationPlugin() = with(project) {
    plugins.apply(ApplicationPlugin::class.java)
    val shadowJarTask = tasks.shadowJar.get()
    val runShadowTask = tasks.runShadow.get()
    val startShadowScripts = tasks.startShadowScripts.get()
    val installShadowDist = tasks.installShadowDist.get()
    val shadowDistZip = tasks.shadowDistZip.get()

    with(runShadowTask) {
      assertThat(description).isEqualTo("Runs this project as a JVM application using the shadow jar")
      assertThat(group).isEqualTo(ApplicationPlugin.APPLICATION_GROUP)
      assertThat(classpath.files).contains(shadowJarTask.archiveFile.get().asFile)
      assertThat(mainModule.orNull).isEqualTo(applicationExtension.mainModule.orNull)
      assertThat(mainClass.orNull).isEqualTo(applicationExtension.mainClass.orNull)
      assertThat(jvmArguments.get()).isEqualTo(applicationExtension.applicationDefaultJvmArgs)
      assertThat(modularity.inferModulePath.orNull)
        .isEqualTo(javaPluginExtension.modularity.inferModulePath.orNull)
      assertThat(javaLauncher.get().metadata.jvmVersion)
        .isEqualTo(javaToolchainService.launcherFor(javaPluginExtension.toolchain).get().metadata.jvmVersion)
    }

    with(startShadowScripts) {
      assertThat(description).isEqualTo("Creates OS specific scripts to run the project as a JVM application using the shadow jar")
      assertThat(group).isEqualTo(ApplicationPlugin.APPLICATION_GROUP)
      assertThat(classpath).isNotNull().transform { it.files }.contains(shadowJarTask.archiveFile.get().asFile)
      assertThat(mainModule.orNull).isEqualTo(applicationExtension.mainModule.orNull)
      assertThat(mainClass.orNull).isEqualTo(applicationExtension.mainClass.orNull)
      assertThat(applicationName).isEqualTo(applicationExtension.applicationName)
      assertThat(outputDir).isNotNull()
        .isEqualTo(layout.buildDirectory.dir("scriptsShadow").get().asFile)
      assertThat(executableDir).isEqualTo(applicationExtension.executableDir)
      assertThat(defaultJvmOpts).isEqualTo(applicationExtension.applicationDefaultJvmArgs)
      assertThat(modularity.inferModulePath.orNull)
        .isEqualTo(javaPluginExtension.modularity.inferModulePath.orNull)
    }

    with(installShadowDist) {
      assertThat(description).isEqualTo("Installs the project as a distribution as-is.")
      assertThat(group).isEqualTo("distribution")
      assertThat(destinationDir).isNotNull()
        .isEqualTo(projectDir.resolve("build/install/my-project-shadow"))
    }

    with(shadowDistZip) {
      assertThat(description).isEqualTo("Bundles the project as a distribution.")
      assertThat(group).isEqualTo("distribution")
      assertThat(archiveAppendix.orNull).isNull()
      assertThat(archiveBaseName.get()).isEqualTo("my-project-shadow")
      assertThat(archiveClassifier.orNull).isNull()
      assertThat(archiveExtension.get()).isEqualTo("zip")
      assertThat(archiveFileName.get()).isEqualTo("my-project-shadow-1.0.0.zip")
      assertThat(archiveVersion.get()).isEqualTo(version)
      assertThat(archiveFile.get().asFile)
        .isEqualTo(destinationDirectory.file(archiveFileName).get().asFile)
      assertThat(destinationDirectory.get().asFile)
        .isEqualTo(layout.buildDirectory.dir("distributions").get().asFile)
    }
  }

  @Test
  fun applyJavaGradlePlugin() = with(project) {
    plugins.apply(JavaGradlePluginPlugin::class.java)
    val api = configurations.named(API_CONFIGURATION_NAME).get()
    val compileOnly = configurations.named(COMPILE_ONLY_CONFIGURATION_NAME).get()
    val gradleApi = dependencies.gradleApi()
    assertThat(api.dependencies).containsNone(gradleApi)
    assertThat(compileOnly.dependencies).contains(gradleApi)
  }

  private companion object {
    const val PROJECT_NAME = "my-project"
    const val VERSION = "1.0.0"

    val Task.dependsOnTaskNames: List<String> get() = dependsOn.filterIsInstance<Named>().map { it.name }
  }
}
