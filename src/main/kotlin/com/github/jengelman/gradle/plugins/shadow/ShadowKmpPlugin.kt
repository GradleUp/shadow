package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.registerShadowJarCommon
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.shadowJar
import com.github.jengelman.gradle.plugins.shadow.internal.javaPluginExtension
import com.github.jengelman.gradle.plugins.shadow.internal.javaToolchainService
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import kotlin.collections.contains
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KgpVersion
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmBinaryDsl
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
          configureMainClass(target.name, task)
        }

//        if (KgpVersion.DEFAULT < KgpVersion.fromVersion("2.1.20")) return@configureEach
        kmpExtension.jvm(target.name).binaries {
          executable {
            project.addRunTask(this)
          }
        }
      }
    }
  }

  private fun Project.configureMainClass(targetName: String, task: ShadowJar) {
    if (KgpVersion.DEFAULT < KgpVersion.KOTLIN_2_1) return

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    kmpExtension.jvm(targetName).mainRun {
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

  private fun Project.addRunTask(dsl: KotlinJvmBinaryDsl) {
    tasks.register(SHADOW_RUN_TASK_NAME, JavaExec::class.java) { task ->
      task.description = "Runs this project as a JVM application using the shadow jar"
      task.group = ApplicationPlugin.APPLICATION_GROUP

      task.mainModule.set(dsl.mainModule)
      task.mainClass.set(dsl.mainClass)
      task.jvmArguments.convention(dsl.applicationDefaultJvmArgs)
      task.classpath(tasks.shadowJar)

      task.modularity.inferModulePath.convention(javaPluginExtension.modularity.inferModulePath)
      task.javaLauncher.convention(javaToolchainService.launcherFor(javaPluginExtension.toolchain))
    }
  }
}
