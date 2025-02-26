package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.registerShadowJarCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

public abstract class ShadowKmpPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    with(project) {
      val kmpExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)
      val kotlinJvmMain = kmpExtension.jvm().compilations.named("main")
      registerShadowJarCommon { task ->
        task.from(kotlinJvmMain.map { it.output.allOutputs })
        task.configurations.convention(
          provider { listOf(configurations.getByName(kotlinJvmMain.get().runtimeDependencyConfigurationName)) },
        )
      }
    }
  }
}
