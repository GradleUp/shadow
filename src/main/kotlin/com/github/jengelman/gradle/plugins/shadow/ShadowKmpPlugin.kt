package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.isAtLeastKgpVersion
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.registerShadowJarCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

public abstract class ShadowKmpPlugin : Plugin<Project> {

  override fun apply(project: Project): Unit = with(project) {
    extensions.getByType(KotlinMultiplatformExtension::class.java).targets.configureEach { target ->
      if (target !is KotlinJvmTarget) return@configureEach

      configureShadowJar(target)
    }
  }

  private fun Project.configureShadowJar(target: KotlinJvmTarget) {
    val kotlinJvmMain = target.compilations.named("main")
    registerShadowJarCommon { task ->
      task.from(kotlinJvmMain.map { it.output.allOutputs })
      task.configurations.convention(
        provider {
          listOf(configurations.getByName(kotlinJvmMain.get().runtimeDependencyConfigurationName))
        },
      )

      if (!isAtLeastKgpVersion(1, 9, 0)) return@registerShadowJarCommon

      @OptIn(ExperimentalKotlinGradlePluginApi::class)
      target.mainRun {
        // Fix cannot serialize object of type 'org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun'.
        val mainClassName = provider { mainClass }
        task.inputs.property("mainClassName", mainClassName)
        task.doFirst {
          val realClass = mainClassName.get().orNull
          if (!task.manifest.attributes.contains(mainClassAttributeKey) && !realClass.isNullOrEmpty()) {
            task.manifest.attributes[mainClassAttributeKey] = realClass
          }
        }
      }
    }
  }
}
