package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.SHADOW
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.internal.applicationExtension
import com.github.jengelman.gradle.plugins.shadow.internal.distributions
import com.github.jengelman.gradle.plugins.shadow.internal.javaPluginExtension
import com.github.jengelman.gradle.plugins.shadow.internal.javaToolchainService
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsText
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator

/**
 * A [Plugin] which packages and runs a project as a Java Application using the shadowed jar.
 *
 * Modified from [org.gradle.api.plugins.ApplicationPlugin.java](https://github.com/gradle/gradle/blob/45a20d82b623786d19b50185e595adf3d7b196b2/platforms/jvm/plugins-application/src/main/java/org/gradle/api/plugins/ApplicationPlugin.java).
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
    tasks.register(SHADOW_RUN_TASK_NAME, JavaExec::class.java) { task ->
      task.description = "Runs this project as a JVM application using the shadow jar"
      task.group = ApplicationPlugin.APPLICATION_GROUP

      task.classpath = files(tasks.shadowJar)

      with(applicationExtension) {
        task.mainModule.convention(mainModule)
        task.mainClass.convention(mainClass)
        task.jvmArguments.convention(provider { applicationDefaultJvmArgs })
      }

      task.modularity.inferModulePath.convention(javaPluginExtension.modularity.inferModulePath)
      task.javaLauncher.convention(javaToolchainService.launcherFor(javaPluginExtension.toolchain))
    }
  }

  protected open fun Project.addCreateScriptsTask() {
    tasks.register(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts::class.java) { task ->
      task.description = "Creates OS specific scripts to run the project as a JVM application using the shadow jar"
      task.group = ApplicationPlugin.APPLICATION_GROUP

      val dir = "com/github/jengelman/gradle/plugins/shadow/internal"
      (task.unixStartScriptGenerator as TemplateBasedScriptGenerator).template =
        resources.text.fromString(requireResourceAsText("$dir/unixStartScript.txt"))
      (task.windowsStartScriptGenerator as TemplateBasedScriptGenerator).template =
        resources.text.fromString(requireResourceAsText("$dir/windowsStartScript.txt"))

      task.classpath = files(tasks.shadowJar)

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
      val applicationName = providers.provider { applicationExtension.applicationName }

      task.doFirst {
        if (
          !task.destinationDir.listFiles().isNullOrEmpty() &&
          (
            !task.destinationDir.resolve("lib").isDirectory ||
              !task.destinationDir.resolve("bin").isDirectory
            )
        ) {
          throw GradleException(
            "The specified installation directory '${task.destinationDir}' is neither empty nor does it contain an installation for '${applicationName.get()}'.\n" +
              "If you really want to install to this directory, delete it and run the install task again.\n" +
              "Alternatively, choose a different installation directory.",
          )
        }
      }
      task.doLast {
        task.eachFile {
          if (it.path == "bin/${applicationName.get()}") {
            it.permissions { permissions -> permissions.unix(UNIX_SCRIPT_PERMISSIONS) }
          }
        }
      }
    }
  }

  protected open fun Project.configureDistribution() {
    distributions.register(DISTRIBUTION_NAME) {
      it.contents { shadowDist ->
        shadowDist.from(file("src/dist"))
        shadowDist.into("lib") { lib ->
          lib.from(tasks.shadowJar)
          lib.from(configurations.shadow)
        }
        shadowDist.into("bin") { bin ->
          bin.from(tasks.startShadowScripts)
          bin.filePermissions { it.unix(UNIX_SCRIPT_PERMISSIONS) }
        }
        shadowDist.with(applicationExtension.applicationDistribution)
      }
    }
  }

  protected open fun Project.configureShadowJarMainClass() {
    val mainClassName = applicationExtension.mainClass
    tasks.shadowJar.configure { task ->
      task.doFirst {
        // Inject the attribute if it is not already present.
        if (!task.manifest.attributes.contains(mainClassAttributeKey)) {
          task.manifest.attributes[mainClassAttributeKey] = mainClassName.orNull.also { value ->
            if (value.isNullOrEmpty()) {
              error("The main class must be specified and not empty in `application.mainClass` or manifest attributes.")
            }
          }
        }
      }
    }
  }

  public companion object {
    /**
     * Reflects the number of 755.
     */
    private const val UNIX_SCRIPT_PERMISSIONS = "rwxr-xr-x"

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
  }
}
