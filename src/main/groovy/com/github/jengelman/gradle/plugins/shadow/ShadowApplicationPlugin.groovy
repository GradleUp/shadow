package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.JavaJarExec
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.toolchain.JavaToolchainService

class ShadowApplicationPlugin implements Plugin<Project> {

    public static final String SHADOW_RUN_TASK_NAME = 'runShadow'
    public static final String SHADOW_SCRIPTS_TASK_NAME = 'startShadowScripts'
    public static final String SHADOW_INSTALL_TASK_NAME = 'installShadowDist'

    private Project project
    private JavaApplication javaApplication

    @Override
    void apply(Project project) {
        this.project = project
        this.javaApplication = project.extensions.getByType(JavaApplication)

        addRunTask()
        addCreateScriptsTask()

        configureDistSpec()

        configureJarMainClass()
        configureInstallTask()
    }

    protected void configureJarMainClass() {
        def classNameProvider = javaApplication.mainClass
        shadowJar.configure { jar ->
            jar.inputs.property('mainClassName', classNameProvider)
            jar.doFirst {
                jar.manifest.attributes 'Main-Class': classNameProvider.get()
            }
        }
    }

    protected void addRunTask() {
        project.tasks.register(SHADOW_RUN_TASK_NAME, JavaJarExec) { run ->
            def install = project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync)
            run.dependsOn install
            run.mainClass.set('-jar')
            run.description = 'Runs this project as a JVM application using the shadow jar'
            run.group = ApplicationPlugin.APPLICATION_GROUP
            run.conventionMapping.jvmArgs = { javaApplication.applicationDefaultJvmArgs }
            run.jarFile.fileProvider(project.providers.provider {
                project.file("${install.get().destinationDir.path}/lib/${shadowJar.get().archiveFile.get().asFile.name}")
            })
            def toolchain = project.getExtensions().getByType(JavaPluginExtension.class).toolchain
            def defaultLauncher = project.getExtensions().getByType(JavaToolchainService.class)
                .launcherFor(toolchain)
            run.getJavaLauncher().set(defaultLauncher)
        }
    }

    protected void addCreateScriptsTask() {
        project.tasks.register(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts) { task ->
            task.unixStartScriptGenerator.template = project.resources.text.fromString(this.class.getResource("internal/unixStartScript.txt").text)
            task.windowsStartScriptGenerator.template = project.resources.text.fromString(this.class.getResource("internal/windowsStartScript.txt").text)
            task.description = 'Creates OS specific scripts to run the project as a JVM application using the shadow jar'
            task.group = ApplicationPlugin.APPLICATION_GROUP
            task.classpath = project.files(shadowJar)
            task.conventionMapping.mainClassName = { javaApplication.mainClass.get() }
            task.conventionMapping.applicationName = { javaApplication.applicationName }
            task.conventionMapping.outputDir = { new File(project.layout.buildDirectory.asFile.get(), 'scriptsShadow') }
            task.conventionMapping.defaultJvmOpts = { javaApplication.applicationDefaultJvmArgs }
            task.inputs.files(project.files(shadowJar))
        }
    }

    protected void configureInstallTask() {
        project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync).configure { task ->
            task.doFirst {
                if (task.destinationDir.directory) {
                    if (task.destinationDir.listFiles().size() != 0 && (!new File(task.destinationDir, 'lib').directory || !new File(task.destinationDir, 'bin').directory)) {
                        throw new GradleException("The specified installation directory '${task.destinationDir}' is neither empty nor does it contain an installation for '${javaApplication.applicationName}'.\n" +
                            "If you really want to install to this directory, delete it and run the install task again.\n" +
                            "Alternatively, choose a different installation directory."
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

    protected void configureDistSpec() {
        project.extensions.getByType(DistributionContainer)
            .create(ShadowBasePlugin.DISTRIBUTION_NAME) { distributions ->
                distributions.contents.with {
                    from(project.file("src/dist"))

                    into("lib") {
                        from(shadowJar)
                        from(project.configurations.named(ShadowBasePlugin.CONFIGURATION_NAME))
                    }
                    into("bin") {
                        from(project.tasks.named(SHADOW_SCRIPTS_TASK_NAME))
                        filePermissions { it.unix(493) }
                    }
                }
            }
    }

    private TaskProvider<ShadowJar> getShadowJar() {
        project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME, ShadowJar)
    }
}
