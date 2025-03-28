package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.registerShadowJarCommon
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import kotlin.collections.contains
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KgpVersion
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

public abstract class ShadowKmpPlugin : Plugin<Project> {
  private lateinit var kmpExtension: KotlinMultiplatformExtension

  override fun apply(project: Project) {
    with(project) {
      kmpExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)
      kmpExtension.targets.configureEach { target ->
        if (target !is KotlinJvmTarget) return@configureEach

        val kotlinJvmMain = target.compilations.named("main")
        registerShadowJarCommon { task ->
          task.from(kotlinJvmMain.map { it.output.allOutputs })
          task.configurations.convention(
            provider {
              listOf(configurations.getByName(kotlinJvmMain.get().runtimeDependencyConfigurationName))
            },
          )
          configureMainClass(task)
        }
      }
    }
  }

  private fun Project.configureMainClass(task: ShadowJar) {
    if (KgpVersion.DEFAULT < KgpVersion.KOTLIN_2_1) return

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    kmpExtension.jvm().mainRun {
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
