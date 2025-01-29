package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.shadowJar
import com.github.jengelman.gradle.plugins.shadow.internal.applicationExtension
import com.github.jengelman.gradle.plugins.shadow.internal.distributions
import com.github.jengelman.gradle.plugins.shadow.internal.javaPluginExtension
import com.github.jengelman.gradle.plugins.shadow.internal.javaToolchainService
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsText
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

public abstract class ShadowApplicationPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.addRunTask()
    project.addCreateScriptsTask()
    project.configureDistSpec()
    project.configureJarMainClass()
    project.configureInstallTask()
  }

  protected open fun Project.configureJarMainClass() {
    val mainClassName = applicationExtension.mainClass
    tasks.shadowJar.configure { task ->
      task.inputs.property("mainClassName", mainClassName)
      task.doFirst {
        // Inject the Main-Class attribute if it is not already present.
        if (!task.manifest.attributes.contains("Main-Class")) {
          task.manifest.attributes["Main-Class"] = mainClassName.get()
        }
      }
    }
  }

  protected open fun Project.addRunTask() {
    val extension = applicationExtension
    tasks.register(SHADOW_RUN_TASK_NAME, JavaExec::class.java) { task ->
      task.description = "Runs this project as a JVM application using the shadow jar"
      task.group = ApplicationPlugin.APPLICATION_GROUP

      val jarFile = tasks.installShadowDist.zip(tasks.shadowJar) { i, s ->
        i.destinationDir.resolve("lib/${s.archiveFile.get().asFile.name}")
      }
      task.classpath(jarFile)
      task.mainClass.set(extension.mainClass)
      task.conventionMapping.map("jvmArgs", extension::getApplicationDefaultJvmArgs)
      task.javaLauncher.set(javaToolchainService.launcherFor(javaPluginExtension.toolchain))
    }
  }

  /**
   * Syncs with [ApplicationPlugin.addCreateScriptsTask](https://github.com/gradle/gradle/blob/bcecbb416f19438c7532e309456e3c3ed287f8f5/platforms/jvm/plugins-application/src/main/java/org/gradle/api/plugins/ApplicationPlugin.java#L184-L203).
   */
  protected open fun Project.addCreateScriptsTask() {
    val extension = applicationExtension
    tasks.register(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts::class.java) { task ->
      task.description = "Creates OS specific scripts to run the project as a JVM application using the shadow jar"
      task.group = ApplicationPlugin.APPLICATION_GROUP

      val dir = "com/github/jengelman/gradle/plugins/shadow/internal"
      (task.unixStartScriptGenerator as TemplateBasedScriptGenerator).template =
        resources.text.fromString(requireResourceAsText("$dir/unixStartScript.txt"))
      (task.windowsStartScriptGenerator as TemplateBasedScriptGenerator).template =
        resources.text.fromString(requireResourceAsText("$dir/windowsStartScript.txt"))

      task.classpath = files(tasks.shadowJar)
      task.mainModule.set(extension.mainModule)
      task.mainClass.set(extension.mainClass)
      task.conventionMapping.map("applicationName", extension::getApplicationName)
      task.conventionMapping.map("outputDir") { layout.buildDirectory.dir("scriptsShadow").get().asFile }
      task.conventionMapping.map("executableDir", extension::getExecutableDir)
      task.conventionMapping.map("defaultJvmOpts", extension::getApplicationDefaultJvmArgs)
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
            it.permissions { permissions -> permissions.unix(755) }
          }
        }
      }
    }
  }

  protected open fun Project.configureDistSpec() {
    distributions.register(ShadowBasePlugin.DISTRIBUTION_NAME) { distributions ->
      distributions.contents { contents ->
        contents.from(file("src/dist"))
        contents.into("lib") { lib ->
          lib.from(tasks.shadowJar)
          lib.from(configurations.shadow)
        }
        contents.into("bin") { bin ->
          bin.from(tasks.startShadowScripts)
          bin.filePermissions { it.unix(493) }
        }
      }
    }
  }

  public companion object {
    public const val SHADOW_RUN_TASK_NAME: String = "runShadow"
    public const val SHADOW_SCRIPTS_TASK_NAME: String = "startShadowScripts"
    public const val SHADOW_INSTALL_TASK_NAME: String = "installShadowDist"

    public inline val TaskContainer.startShadowScripts: TaskProvider<CreateStartScripts>
      get() = named(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts::class.java)

    public inline val TaskContainer.installShadowDist: TaskProvider<Sync>
      get() = named(SHADOW_INSTALL_TASK_NAME, Sync::class.java)
  }
}
