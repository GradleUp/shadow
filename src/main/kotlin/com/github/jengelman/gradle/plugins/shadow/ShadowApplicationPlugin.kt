package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.JavaJarExec
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsText
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator
import org.gradle.jvm.toolchain.JavaToolchainService

public class ShadowApplicationPlugin : Plugin<Project> {
  private lateinit var project: Project
  private lateinit var javaApplication: JavaApplication

  override fun apply(project: Project) {
    this.project = project
    this.javaApplication = project.extensions.getByType(JavaApplication::class.java)

    addRunTask()
    addCreateScriptsTask()
    configureDistSpec()
    configureJarMainClass()
    configureInstallTask()
  }

  private fun configureJarMainClass() {
    val classNameProvider = javaApplication.mainClass
    shadowJar.configure { jar ->
      jar.inputs.property("mainClassName", classNameProvider)
      jar.doFirst {
        jar.manifest.attributes["Main-Class"] = classNameProvider.get()
      }
    }
  }

  private fun addRunTask() {
    project.tasks.register(SHADOW_RUN_TASK_NAME, JavaJarExec::class.java) {
      val install = project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync::class.java)
      it.dependsOn(install)
      it.mainClass.set("-jar")
      it.description = "Runs this project as a JVM application using the shadow jar"
      it.group = ApplicationPlugin.APPLICATION_GROUP
      it.conventionMapping.map("jvmArgs") { javaApplication.applicationDefaultJvmArgs }
      it.jarFile.fileProvider(
        project.providers.provider {
          project.file("${install.get().destinationDir.path}/lib/${shadowJar.get().archiveFile.get().asFile.name}")
        },
      )
      val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
      val defaultLauncher = project.extensions.getByType(JavaToolchainService::class.java)
        .launcherFor(toolchain)
      it.javaLauncher.set(defaultLauncher)
    }
  }

  private fun addCreateScriptsTask() {
    project.tasks.register(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts::class.java) {
      (it.unixStartScriptGenerator as TemplateBasedScriptGenerator).template =
        project.resources.text.fromString(this::class.java.requireResourceAsText("internal/unixStartScript.txt"))
      (it.windowsStartScriptGenerator as TemplateBasedScriptGenerator).template =
        project.resources.text.fromString(this::class.java.requireResourceAsText("internal/windowsStartScript.txt"))
      it.description = "Creates OS specific scripts to run the project as a JVM application using the shadow jar"
      it.group = ApplicationPlugin.APPLICATION_GROUP
      it.classpath = project.files(shadowJar)
      it.conventionMapping.map("mainClassName") { javaApplication.mainClass.get() }
      it.conventionMapping.map("applicationName") { javaApplication.applicationName }
      it.conventionMapping.map("outputDir") { project.layout.buildDirectory.dir("scriptsShadow").get().asFile }
      it.conventionMapping.map("defaultJvmOpts") { javaApplication.applicationDefaultJvmArgs }
      it.inputs.files(project.files(shadowJar))
    }
  }

  private fun configureInstallTask() {
    project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync::class.java).configure { task ->
      val applicationName = project.providers.provider { javaApplication.applicationName }

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
            it.mode = 0x755
          }
        }
      }
    }
  }

  private fun configureDistSpec() {
    project.extensions.getByType(DistributionContainer::class.java)
      .register(ShadowBasePlugin.DISTRIBUTION_NAME) { distributions ->
        distributions.contents { contents ->
          contents.from(project.file("src/dist"))
          contents.into("lib") { lib ->
            lib.from(shadowJar)
            lib.from(project.configurations.named(ShadowBasePlugin.CONFIGURATION_NAME))
          }
          contents.into("bin") { bin ->
            bin.from(project.tasks.named(SHADOW_SCRIPTS_TASK_NAME))
            bin.filePermissions { it.unix(493) }
          }
        }
      }
  }

  private val shadowJar: TaskProvider<ShadowJar>
    get() = project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME, ShadowJar::class.java)

  public companion object {
    public const val SHADOW_RUN_TASK_NAME: String = "runShadow"
    public const val SHADOW_SCRIPTS_TASK_NAME: String = "startShadowScripts"
    public const val SHADOW_INSTALL_TASK_NAME: String = "installShadowDist"
  }
}
