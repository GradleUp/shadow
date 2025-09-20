package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.internal.runtimeConfiguration
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ShadowPropertiesTest {
  private lateinit var project: Project

  @BeforeEach
  fun beforeEach() {
    project = ProjectBuilder.builder().withName(PROJECT_NAME).build().also {
      it.version = VERSION
    }
  }

  @Test
  fun applyJavaPlugin() = with(project) {
    plugins.apply(ShadowPlugin::class.java)

    assertThat(plugins.hasPlugin(ShadowPlugin::class.java)).isTrue()
    assertThat(plugins.hasPlugin(LegacyShadowPlugin::class.java)).isTrue()
    assertThat(tasks.findByName(SHADOW_JAR_TASK_NAME)).isNull()

    with(extensions.getByType(ShadowExtension::class.java)) {
      assertThat(addShadowVariantIntoJavaComponent.get()).isTrue()
      assertThat(addTargetJvmVersionAttribute.get()).isTrue()
    }

    plugins.apply(JavaPlugin::class.java)
    val shadowTask = tasks.getByName(SHADOW_JAR_TASK_NAME) as ShadowJar
    val shadowConfig = configurations.getByName(ShadowBasePlugin.CONFIGURATION_NAME)
    val assembleTask = tasks.getByName(ASSEMBLE_TASK_NAME)

    assertThat(shadowConfig.artifacts.files).contains(shadowTask.archiveFile.get().asFile)
    assertThat(assembleTask.dependsOn.filterIsInstance<Named>().map { it.name }).all {
      isNotEmpty()
      contains(shadowTask.name)
    }

    // Check inherited properties.
    with(shadowTask as Jar) {
      assertThat(group).isEqualTo(LifecycleBasePlugin.BUILD_GROUP)
      assertThat(description).isEqualTo("Create a combined JAR of project and runtime dependencies")

      assertThat(archiveAppendix.orNull).isNull()
      assertThat(archiveBaseName.get()).isEqualTo(PROJECT_NAME)
      assertThat(archiveClassifier.get()).isEqualTo("all")
      assertThat(archiveExtension.get()).isEqualTo("jar")
      assertThat(archiveFileName.get()).isEqualTo("my-shadow-1.0.0-all.jar")
      assertThat(archiveVersion.get()).isEqualTo(version)
      assertThat(archiveFile.get().asFile).all {
        isEqualTo(destinationDirectory.file(archiveFileName).get().asFile)
        isEqualTo(projectDir.resolve("build/libs/my-shadow-1.0.0-all.jar"))
      }
      assertThat(destinationDirectory.get().asFile)
        .isEqualTo(layout.buildDirectory.dir("libs").get().asFile)

      assertThat(duplicatesStrategy).isEqualTo(DuplicatesStrategy.EXCLUDE)
    }

    // Check self properties.
    with(shadowTask) {
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

  private companion object {
    const val PROJECT_NAME = "my-shadow"
    const val VERSION = "1.0.0"
  }
}
