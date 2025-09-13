package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.internal.isAtLeastKgpVersion
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.registerShadowJarCommon
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmBinariesDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

public abstract class ShadowKmpPlugin : Plugin<Project> {

  override fun apply(project: Project): Unit = with(project) {
    extensions.getByType(KotlinMultiplatformExtension::class.java).targets.configureEach { target ->
      if (target !is KotlinJvmTarget) return@configureEach
      @Suppress("EagerGradleConfiguration")
      if (tasks.findByName(SHADOW_JAR_TASK_NAME) != null) {
        // Declaring multiple Kotlin Targets of the same type is not supported. See https://kotl.in/declaring-multiple-targets for more details.
        logger.info("$SHADOW_JAR_TASK_NAME task already exists, skipping configuration for target: ${target.name}")
        return@configureEach
      }

      configureShadowJar(target)
      addRunTask(target)
    }
  }

  private fun Project.configureShadowJar(target: KotlinJvmTarget) {
    val kotlinJvmMain = target.compilations.named("main")
    registerShadowJarCommon(tasks.named(target.artifactsTaskName, Jar::class.java)) { task ->
      task.from(kotlinJvmMain.map { it.output.allOutputs })
      task.configurations.convention(
        provider {
          listOf(configurations.getByName(kotlinJvmMain.get().runtimeDependencyConfigurationName))
        },
      )

      if (!isAtLeastKgpVersion(1, 9, 0)) return@registerShadowJarCommon

      @OptIn(ExperimentalKotlinGradlePluginApi::class)
      target.mainRun {
        task.mainClass.convention(mainClass)
      }
    }
  }

  private fun Project.addRunTask(target: KotlinJvmTarget) {
    if (!isAtLeastKgpVersion(2, 1, 20)) return

    tasks.register(SHADOW_RUN_TASK_NAME, JavaExec::class.java) { task ->
      task.description = "Runs this project as a JVM application using the shadow jar"
      task.group = ApplicationPlugin.APPLICATION_GROUP

      task.classpath = files(tasks.shadowJar)

      val binariesDsl = target::class.memberProperties
        .single { it.name == "binariesDsl" }
        .apply { isAccessible = true }
        .getter.call(target) as KotlinJvmBinariesDsl

      binariesDsl.executable { dsl ->
        task.mainModule.set(dsl.mainModule)
        task.mainClass.set(dsl.mainClass)
        task.jvmArguments.convention(dsl.applicationDefaultJvmArgs)
      }
    }
  }
}
