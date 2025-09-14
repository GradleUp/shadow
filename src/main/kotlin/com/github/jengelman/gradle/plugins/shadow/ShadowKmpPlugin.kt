package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.UNIX_SCRIPT_PERMISSIONS
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.registerRunShadowCommon
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.registerShadowDistributionCommon
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.registerStartShadowScriptsCommon
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.startShadowScripts
import com.github.jengelman.gradle.plugins.shadow.internal.isAtLeastKgpVersion
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.registerShadowJarCommon
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import java.util.Locale
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

public abstract class ShadowKmpPlugin : Plugin<Project> {

  override fun apply(project: Project): Unit = with(project) {
    var jvmTarget: KotlinJvmTarget? = null

    extensions.getByType(KotlinMultiplatformExtension::class.java).targets.configureEach { target ->
      if (target !is KotlinJvmTarget) return@configureEach
      @Suppress("EagerGradleConfiguration")
      if (tasks.findByName(SHADOW_JAR_TASK_NAME) != null) {
        // Declaring multiple Kotlin Targets of the same type is not supported. See https://kotl.in/declaring-multiple-targets for more details.
        logger.info("$SHADOW_JAR_TASK_NAME task already exists, skipping configuration for target: ${target.name}")
        return@configureEach
      }

      configureShadowJar(target)
      jvmTarget = target
    }

    // TODO: https://youtrack.jetbrains.com/issue/KT-77499
    afterEvaluate {
      if (!isAtLeastKgpVersion(2, 1, 20)) return@afterEvaluate
      jvmTarget ?: return@afterEvaluate
      val targetNameCap = jvmTarget.targetName.replaceFirstChar { it.titlecase(Locale.US) }

      @Suppress("EagerGradleConfiguration") // TODO: https://issuetracker.google.com/issues/444825893
      (tasks.findByName("run$targetNameCap") as? JavaExec)?.let {
        addRunTask(it)
      } ?: return@afterEvaluate
      // This task must exist if the runJvmTask exists.
      @Suppress("EagerGradleConfiguration") // TODO: https://issuetracker.google.com/issues/444825893
      (tasks.getByName("startScriptsFor$targetNameCap") as CreateStartScripts).let {
        addCreateScriptsTask(it)
      }
      configureDistribution()
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

  private fun Project.addRunTask(runJvmTask: JavaExec) {
    tasks.shadowJar.configure { task ->
      task.mainClass.convention(runJvmTask.mainClass)
    }
    registerRunShadowCommon { task ->
      with(runJvmTask) {
        task.mainModule.convention(mainModule)
        task.mainClass.convention(mainClass)
        task.jvmArguments.convention(jvmArguments)
        task.modularity.inferModulePath.convention(modularity.inferModulePath)
        task.javaLauncher.convention(javaLauncher)
      }
    }
  }

  private fun Project.addCreateScriptsTask(startScriptsTask: CreateStartScripts) {
    registerStartShadowScriptsCommon { task ->
      @Suppress("InternalGradleApiUsage", "DuplicatedCode") // Usages of conventionMapping.
      with(startScriptsTask) {
        task.mainModule.convention(mainModule)
        task.mainClass.convention(mainClass)
        task.conventionMapping.map("applicationName", ::getApplicationName)
        task.conventionMapping.map("outputDir") { layout.buildDirectory.dir("scriptsShadow").get().asFile }
        task.conventionMapping.map("executableDir", ::getExecutableDir)
        task.conventionMapping.map("defaultJvmOpts", ::getDefaultJvmOpts)
      }
    }
  }

  private fun Project.configureDistribution() {
    registerShadowDistributionCommon { dist ->
      dist.contents { distSpec ->
        // Should use KotlinJvmBinaryDsl.applicationDistribution instead.
        distSpec.into("bin") { bin ->
          bin.from(tasks.startShadowScripts)
          bin.filePermissions { permissions -> permissions.unix(UNIX_SCRIPT_PERMISSIONS) }
        }
        // TODO: we can't access KotlinJvmBinaryDsl instance for now.
//      distSpec.with(KotlinJvmBinaryDsl.applicationDistribution)
      }
    }
  }
}
