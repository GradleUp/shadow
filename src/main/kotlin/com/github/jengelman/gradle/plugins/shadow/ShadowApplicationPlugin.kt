package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.JavaJarExec
import com.github.jengelman.gradle.plugins.shadow.internal.Utils
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.plugins.DefaultTemplateBasedStartScriptGenerator
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.toolchain.JavaToolchainService

class ShadowApplicationPlugin : Plugin<Project> {
  private val shadowJar: TaskProvider<ShadowJar>
    get() = project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME, ShadowJar::class.java)

  private lateinit var project: Project
  private lateinit var javaApplication: JavaApplication

  override fun apply(project: Project) {
    this.project = project
    this.javaApplication = project.extensions.getByType(JavaApplication::class.java)

    val distributions = project.extensions.getByName("distributions") as DistributionContainer
    val distribution = distributions.create("shadow")

    addRunTask(project)
    addCreateScriptsTask(project)
    configureDistSpec(project, distribution.contents)
    configureJarMainClass()
    configureInstallTask(project)
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

  private fun addRunTask(project: Project) {
    project.tasks.register(SHADOW_RUN_TASK_NAME, JavaJarExec::class.java) { run ->
      val install = project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync::class.java)
      run.dependsOn(SHADOW_INSTALL_TASK_NAME)
      run.mainClass.set("-jar")
      run.description = "Runs this project as a JVM application using the shadow jar"
      run.group = ApplicationPlugin.APPLICATION_GROUP
      run.conventionMapping.map("jvmArgs") { javaApplication.applicationDefaultJvmArgs }
      run.conventionMapping.map("jarFile") {
        project.file("${install.get().destinationDir.path}/lib/${shadowJar.get().archiveFile.get().asFile.name}")
      }
      configureJavaLauncher(run)
    }
  }

  private fun configureJavaLauncher(run: JavaJarExec) {
    val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
    val service = project.extensions.getByType(JavaToolchainService::class.java)
    val defaultLauncher = service.launcherFor(toolchain)
    run.javaLauncher.set(defaultLauncher)
  }

  private fun addCreateScriptsTask(project: Project) {
    project.tasks.register(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts::class.java) { startScripts ->
      (startScripts.unixStartScriptGenerator as DefaultTemplateBasedStartScriptGenerator).template =
        project.resources.text.fromString(Utils.requireResourceAsText("internal/unixStartScript.txt"))
      (startScripts.windowsStartScriptGenerator as DefaultTemplateBasedStartScriptGenerator).template =
        project.resources.text.fromString(Utils.requireResourceAsText("internal/windowsStartScript.txt"))
      startScripts.description =
        "Creates OS specific scripts to run the project as a JVM application using the shadow jar"
      startScripts.group = ApplicationPlugin.APPLICATION_GROUP
      startScripts.classpath = project.files(shadowJar)
      startScripts.conventionMapping.map("mainClassName") { javaApplication.mainClass.get() }
      startScripts.conventionMapping.map("applicationName") { javaApplication.applicationName }
      startScripts.conventionMapping.map("outputDir") {
        project.layout.buildDirectory.dir("scriptsShadow").get().asFile
      }
      startScripts.conventionMapping.map("defaultJvmOpts") { javaApplication.applicationDefaultJvmArgs }
      startScripts.inputs.files(project.objects.fileCollection().from(shadowJar))
    }
  }

  private fun configureInstallTask(project: Project) {
    project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync::class.java).configure { task ->
      task.doFirst {
        if (task.destinationDir.isDirectory) {
          if (task.destinationDir.listFiles()?.isNotEmpty() == true &&
            (!File(task.destinationDir, "lib").isDirectory || !File(task.destinationDir, "bin").isDirectory)
          ) {
            throw GradleException(
              "The specified installation directory '${task.destinationDir}' is neither empty nor does it contain an installation for '${javaApplication.applicationName}'.\n" +
                "If you really want to install to this directory, delete it and run the install task again.\n" +
                "Alternatively, choose a different installation directory.",
            )
          }
        }
      }
      task.doLast {
        task.eachFile {
          if (it.path == "bin/${javaApplication.applicationName}") {
            it.mode = 0x755
          }
        }
      }
    }
  }

  private fun configureDistSpec(project: Project, distSpec: CopySpec): CopySpec {
    val startScripts = project.tasks.named(SHADOW_SCRIPTS_TASK_NAME)
    return distSpec.apply {
      from(project.file("src/dist"))

      into("lib") {
        from(shadowJar)
        from(project.configurations.getByName(ShadowBasePlugin.CONFIGURATION_NAME))
      }
      into("bin") {
        from(startScripts)
        filePermissions { it.unix(493) }
      }
    }
  }

  companion object {
    const val SHADOW_RUN_TASK_NAME = "runShadow"
    const val SHADOW_SCRIPTS_TASK_NAME = "startShadowScripts"
    const val SHADOW_INSTALL_TASK_NAME = "installShadowDist"
  }
}
