package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.SHADOW
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.internal.applicationExtension
import com.github.jengelman.gradle.plugins.shadow.internal.distributions
import com.github.jengelman.gradle.plugins.shadow.internal.javaPluginExtension
import com.github.jengelman.gradle.plugins.shadow.internal.javaToolchainService
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import java.io.IOException
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.Distribution
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.application.CreateStartScripts

/**
 * A [Plugin] which packages and runs a project as a Java Application using the shadowed jar.
 *
 * Modified from [org.gradle.api.plugins.ApplicationPlugin.java](https://github.com/gradle/gradle/blob/fdecc3c95828bb9a1c1bb6114483fe5b16f9159d/platforms/jvm/plugins-application/src/main/java/org/gradle/api/plugins/ApplicationPlugin.java).
 *
 * @see [ApplicationPlugin]
 */
public abstract class ShadowApplicationPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit = with(project) {
    addRunTask()
    addCreateScriptsTask()
    configureDistribution()
    configureShadowJarMainClass()
    configureInstallTask()
  }

  protected open fun Project.addRunTask() {
    registerRunShadowCommon { task ->
      with(applicationExtension) {
        task.mainModule.convention(mainModule)
        task.mainClass.convention(mainClass)
        task.jvmArguments.convention(provider(::getApplicationDefaultJvmArgs))
      }
      task.modularity.inferModulePath.convention(javaPluginExtension.modularity.inferModulePath)
      task.javaLauncher.convention(javaToolchainService.launcherFor(javaPluginExtension.toolchain))
    }
  }

  protected open fun Project.addCreateScriptsTask() {
    registerStartShadowScriptsCommon { task ->
      @Suppress("InternalGradleApiUsage", "DuplicatedCode") // Usages of conventionMapping.
      with(applicationExtension) {
        task.mainModule.convention(mainModule)
        task.mainClass.convention(mainClass)
        task.conventionMapping.map("applicationName", ::getApplicationName)
        task.conventionMapping.map("outputDir") { layout.buildDirectory.dir("scriptsShadow").get().asFile }
        task.conventionMapping.map("executableDir", ::getExecutableDir)
        task.conventionMapping.map("defaultJvmOpts", ::getApplicationDefaultJvmArgs)
      }
      task.modularity.inferModulePath.convention(javaPluginExtension.modularity.inferModulePath)
    }
  }

  protected open fun Project.configureInstallTask() {
    tasks.installShadowDist.configure { task ->
      val applicationName = provider(applicationExtension::getApplicationName)
      val executableDir = provider(applicationExtension::getExecutableDir)

      task.doFirst("Check installation directory") {
        val destinationDir = task.destinationDir
        val children = destinationDir.list() ?: throw IOException("Could not list directory $destinationDir")
        if (children.isEmpty()) return@doFirst
        if (
          !destinationDir.resolve("lib").isDirectory ||
          !destinationDir.resolve("bin").isDirectory ||
          !destinationDir.resolve(executableDir.get()).isDirectory
        ) {
          throw GradleException(
            "The specified installation directory '$destinationDir' is neither empty nor does it contain an installation for '${applicationName.get()}'.\n" +
              "If you really want to install to this directory, delete it and run the install task again.\n" +
              "Alternatively, choose a different installation directory.",
          )
        }
      }
    }
  }

  protected open fun Project.configureDistribution() {
    registerShadowDistributionCommon { dist ->
      dist.distributionBaseName.convention(
        provider {
          // distributionBaseName defaults to `$project.name-$distribution.name`, applicationName defaults to project.name
          // so we append the suffix to match the default distributionBaseName. Modified from `ApplicationPlugin.configureDistribution()`.
          "${applicationExtension.applicationName}-$DISTRIBUTION_NAME"
        },
      )
      dist.contents { distSpec ->
        // Defaults to bin dir.
        distSpec.into(provider(applicationExtension::getExecutableDir)) { bin ->
          bin.from(tasks.startShadowScripts)
          bin.filePermissions { permissions -> permissions.unix(UNIX_SCRIPT_PERMISSIONS) }
        }
        distSpec.with(applicationExtension.applicationDistribution)
      }
    }
  }

  protected open fun Project.configureShadowJarMainClass() {
    tasks.shadowJar.configure { task ->
      task.mainClass.convention(applicationExtension.mainClass)
    }
  }

  public companion object {
    /**
     * Reflects the number of 755.
     */
    internal const val UNIX_SCRIPT_PERMISSIONS = "rwxr-xr-x"

    public const val DISTRIBUTION_NAME: String = SHADOW

    public const val SHADOW_RUN_TASK_NAME: String = "runShadow"
    public const val SHADOW_SCRIPTS_TASK_NAME: String = "startShadowScripts"
    public const val SHADOW_INSTALL_TASK_NAME: String = "installShadowDist"

    @get:JvmSynthetic
    public inline val TaskContainer.startShadowScripts: TaskProvider<CreateStartScripts>
      get() = named(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts::class.java)

    @get:JvmSynthetic
    public inline val TaskContainer.installShadowDist: TaskProvider<Sync>
      get() = named(SHADOW_INSTALL_TASK_NAME, Sync::class.java)

    internal fun Project.registerRunShadowCommon(
      action: Action<JavaExec>,
    ): TaskProvider<JavaExec> {
      return tasks.register(SHADOW_RUN_TASK_NAME, JavaExec::class.java) { task ->
        task.description = "Runs this project as a JVM application using the shadow jar"
        task.group = ApplicationPlugin.APPLICATION_GROUP
        task.classpath = files(tasks.shadowJar)
        action.execute(task)
      }
    }

    internal fun Project.registerStartShadowScriptsCommon(
      action: Action<CreateStartScripts>,
    ): TaskProvider<CreateStartScripts> {
      return tasks.register(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts::class.java) { task ->
        task.description = "Creates OS specific scripts to run the project as a JVM application using the shadow jar"
        task.group = ApplicationPlugin.APPLICATION_GROUP
        task.classpath = files(tasks.shadowJar)
        action.execute(task)
      }
    }

    internal fun Project.registerShadowDistributionCommon(
      action: Action<Distribution>,
    ): Provider<Distribution> {
      return distributions.register(DISTRIBUTION_NAME) { dist ->
        dist.contents { distSpec ->
          distSpec.from(file("src/dist"))
          distSpec.into("lib") { lib ->
            lib.from(tasks.shadowJar)
            // Reflects the value of the `Class-Path` attribute in the JAR manifest.
            lib.from(configurations.shadow)
          }
        }
        action.execute(dist)
      }
    }
  }
}
