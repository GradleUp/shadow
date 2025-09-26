package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.containsNone
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.installShadowDist
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.runShadow
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.shadowDistTar
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.shadowDistZip
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.startShadowScripts
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.internal.applicationExtension
import com.github.jengelman.gradle.plugins.shadow.internal.javaPluginExtension
import com.github.jengelman.gradle.plugins.shadow.internal.javaToolchainService
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.runtimeConfiguration
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Bundling
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
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
      assertThat(bundlingAttribute.get()).isEqualTo(Bundling.SHADOWED)
    }
  }

  @Test
  fun inheritManifestAttrsFromJars() = with(project) {
    plugins.apply(JavaPlugin::class.java)
    tasks.jar.configure {
      it.manifest.attributes["jar"] = "fromJar"
    }
    val jar1 = tasks.register("jar1", Jar::class.java) {
      it.manifest.attributes["jar1"] = "fromJar1"
    }
    val jar2 = tasks.register("jar2", Jar::class.java) {
      it.manifest.attributes["jar2"] = "fromJar2"
    }
    tasks.shadowJar.configure {
      it.manifest.attributes["shadowJar"] = "fromShadowJar"
      it.manifest.from(jar1.get().manifest)
      @Suppress("DEPRECATION") // TODO: remove this once InheritManifest is removed.
      it.manifest.inheritFrom(jar2.get().manifest)
    }
    // Call effectiveManifest as a way to force merging to happen like writing the jar would.
    assertThat(tasks.shadowJar.get().manifest.effectiveManifest.attributes).containsOnly(
      "Manifest-Version" to "1.0",
      "jar" to "fromJar",
      "jar1" to "fromJar1",
      "jar2" to "fromJar2",
      "shadowJar" to "fromShadowJar",
    )
  }

  @Test
  fun inheritManifestMainClassFromJar() = with(project) {
    plugins.apply(JavaPlugin::class.java)
    tasks.jar.configure {
      it.manifest.attributes[mainClassAttributeKey] = "Main"
    }
    tasks.shadowJar.configure {
      it.mainClass.set("Main2") // This should not override the inherited one from jar.
    }
    assertThat(tasks.shadowJar.get().manifest.attributes).containsOnly(
      "Manifest-Version" to "1.0",
      mainClassAttributeKey to "Main",
    )
  }

  @Test
  fun applyJavaPlugin() = with(project) {
    plugins.apply(JavaPlugin::class.java)
    val shadowJarTask = tasks.shadowJar.get()
    val shadowConfig = configurations.shadow.get()
    val assembleTask = tasks.getByName(ASSEMBLE_TASK_NAME)

    assertThat(shadowConfig.artifacts.files).containsOnly(shadowJarTask.archiveFile.get().asFile)
    assertThat(assembleTask.dependsOnTaskNames).containsOnly(shadowJarTask.name)

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
      assertThat(destinationDirectory.get().asFile).all {
        isEqualTo(layout.buildDirectory.dir("libs").get().asFile)
        isEqualTo(projectDir.resolve("build/libs"))
      }

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
      assertThat(configurations.get()).containsOnly(runtimeConfiguration)
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
    val shadowDistTar = tasks.shadowDistTar.get()

    with(runShadowTask) {
      assertThat(description).isEqualTo("Runs this project as a JVM application using the shadow jar")
      assertThat(group).isEqualTo(ApplicationPlugin.APPLICATION_GROUP)
      assertThat(classpath.files).containsOnly(shadowJarTask.archiveFile.get().asFile)
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
      assertThat(classpath?.files).isNotNull().containsOnly(shadowJarTask.archiveFile.get().asFile)
      assertThat(mainModule.orNull).isEqualTo(applicationExtension.mainModule.orNull)
      assertThat(mainClass.orNull).isEqualTo(applicationExtension.mainClass.orNull)
      assertThat(applicationName).isEqualTo(applicationExtension.applicationName)
      assertThat(outputDir).isNotNull().all {
        isEqualTo(layout.buildDirectory.dir("scriptsShadow").get().asFile)
        isEqualTo(projectDir.resolve("build/scriptsShadow"))
      }
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

    listOf(
      shadowDistZip,
      shadowDistTar,
    ).forEach {
      with(it as AbstractArchiveTask) {
        assertThat(description).isEqualTo("Bundles the project as a distribution.")
        assertThat(group).isEqualTo("distribution")
        assertThat(archiveAppendix.orNull).isNull()
        assertThat(archiveBaseName.get()).isEqualTo("my-project-shadow")
        assertThat(archiveClassifier.orNull).isNull()
        assertThat(archiveVersion.get()).isEqualTo(version)
        assertThat(destinationDirectory.get().asFile).all {
          isEqualTo(layout.buildDirectory.dir("distributions").get().asFile)
          isEqualTo(projectDir.resolve("build/distributions"))
        }
      }
    }
    with(shadowDistZip) {
      assertThat(archiveExtension.get()).isEqualTo("zip")
      assertThat(archiveFileName.get()).isEqualTo("my-project-shadow-1.0.0.zip")
      assertThat(archiveFile.get().asFile).all {
        isEqualTo(destinationDirectory.file(archiveFileName).get().asFile)
        isEqualTo(projectDir.resolve("build/distributions/my-project-shadow-1.0.0.zip"))
      }
    }
    with(shadowDistTar) {
      assertThat(archiveExtension.get()).isEqualTo("tar")
      assertThat(archiveFileName.get()).isEqualTo("my-project-shadow-1.0.0.tar")
      assertThat(archiveFile.get().asFile).all {
        isEqualTo(destinationDirectory.file(archiveFileName).get().asFile)
        isEqualTo(projectDir.resolve("build/distributions/my-project-shadow-1.0.0.tar"))
      }
    }
  }

  @Test
  fun applyJavaGradlePlugin() = with(project) {
    plugins.apply(JavaGradlePluginPlugin::class.java)
    val api = configurations.named(API_CONFIGURATION_NAME).get()
    val compileOnly = configurations.named(COMPILE_ONLY_CONFIGURATION_NAME).get()
    val gradleApi = dependencies.gradleApi()
    assertThat(api.dependencies).containsNone(gradleApi)
    assertThat(compileOnly.dependencies).containsOnly(gradleApi)
  }

  private companion object {
    const val PROJECT_NAME = "my-project"
    const val VERSION = "1.0.0"

    val Task.dependsOnTaskNames: List<String> get() = dependsOn.filterIsInstance<Named>().map(Named::getName)

    val TaskContainer.jar: TaskProvider<Jar> get() = named("jar", Jar::class.java)
  }
}
