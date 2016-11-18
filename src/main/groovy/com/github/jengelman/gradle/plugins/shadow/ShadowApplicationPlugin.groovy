package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.JavaJarExec
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip

class ShadowApplicationPlugin implements Plugin<Project> {

    static final String SHADOW_RUN_TASK_NAME = 'runShadow'
    static final String SHADOW_SCRIPTS_TASK_NAME = 'startShadowScripts'
    static final String SHADOW_INSTALL_TASK_NAME = 'installShadowApp'
    static final String SHADOW_ZIP_DIST_TASK_NAME = 'distShadowZip'
    static final String SHADOW_TAR_DIST_TASK_NAME = 'distShadowTar'

    private Project project

    @Override
    void apply(Project project) {
        this.project = project

        addRunTask(project)
        addCreateScriptsTask(project)

        ShadowExtension extension = project.extensions.findByType(ShadowExtension)
        configureDistSpec(project, extension.applicationDistribution)

        configureJarMainClass(project)
        addInstallTask(project)
        addDistZipTask(project)
        addDistTarTask(project)
    }

    protected void configureJarMainClass(Project project) {
        ApplicationPluginConvention pluginConvention = (
                ApplicationPluginConvention) project.convention.plugins.application

        jar.inputs.property('mainClassName', pluginConvention.mainClassName)
        jar.doFirst {
            manifest.attributes 'Main-Class': pluginConvention.mainClassName
        }
    }

    protected void addRunTask(Project project) {
        ApplicationPluginConvention pluginConvention = (
                ApplicationPluginConvention) project.convention.plugins.application

        def run = project.tasks.create(SHADOW_RUN_TASK_NAME, JavaJarExec)
        run.dependsOn SHADOW_INSTALL_TASK_NAME
        run.description  = 'Runs this project as a JVM application using the shadow jar'
        run.group = ApplicationPlugin.APPLICATION_GROUP
        run.conventionMapping.jvmArgs = { pluginConvention.applicationDefaultJvmArgs }
        run.conventionMapping.jarFile = {
            project.file("${project.buildDir}/installShadow/${pluginConvention.applicationName}/lib/${jar.archivePath.name}")
        }
    }

    protected void addCreateScriptsTask(Project project) {
        ApplicationPluginConvention pluginConvention =
                (ApplicationPluginConvention) project.convention.plugins.application

        def startScripts = project.tasks.create(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts)
        startScripts.unixStartScriptGenerator.template = project.resources.text.fromString(this.class.getResource("internal/unixStartScript.txt").text)
        startScripts.windowsStartScriptGenerator.template = project.resources.text.fromString(this.class.getResource("internal/windowsStartScript.txt").text)
        startScripts.description = 'Creates OS specific scripts to run the project as a JVM application using the shadow jar'
        startScripts.group = ApplicationPlugin.APPLICATION_GROUP
        startScripts.classpath = project.files(jar)
        startScripts.conventionMapping.mainClassName = { pluginConvention.mainClassName }
        startScripts.conventionMapping.applicationName = { pluginConvention.applicationName }
        startScripts.conventionMapping.outputDir = { new File(project.buildDir, 'scriptsShadow') }
        startScripts.conventionMapping.defaultJvmOpts = { pluginConvention.applicationDefaultJvmArgs }
        startScripts.inputs.file jar

    }

    protected void addInstallTask(Project project) {
        ApplicationPluginConvention pluginConvention =
                (ApplicationPluginConvention) project.convention.plugins.application
        ShadowExtension extension = project.extensions.findByType(ShadowExtension)

        def installTask = project.tasks.create(SHADOW_INSTALL_TASK_NAME, Sync)
        installTask.description = "Installs the project as a JVM application along with libs and OS specific scripts."
        installTask.group = ApplicationPlugin.APPLICATION_GROUP
        installTask.with extension.applicationDistribution
        installTask.into { project.file("${project.buildDir}/installShadow/${pluginConvention.applicationName}") }
        installTask.doFirst {
            if (destinationDir.directory) {
                if (!new File(destinationDir, 'lib').directory || !new File(destinationDir, 'bin').directory) {
                    throw new GradleException("The specified installation directory '${destinationDir}' is neither empty nor does it contain an installation for '${pluginConvention.applicationName}'.\n" +
                            "If you really want to install to this directory, delete it and run the install task again.\n" +
                            "Alternatively, choose a different installation directory."
                    )
                }
            }
        }
        installTask.doLast {
            project.ant.chmod(file: "${destinationDir.absolutePath}/bin/${pluginConvention.applicationName}", perm: 'ugo+x')
        }
    }

    protected void addDistZipTask(Project project) {
        addArchiveTask(project, SHADOW_ZIP_DIST_TASK_NAME, Zip)
    }

    protected void addDistTarTask(Project project) {
        addArchiveTask(project, SHADOW_TAR_DIST_TASK_NAME, Tar)
    }

    protected <T extends AbstractArchiveTask> void addArchiveTask(Project project, String name, Class<T> type) {
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        ShadowExtension extension = project.extensions.findByType(ShadowExtension)

        def archiveTask = project.tasks.create(name, type)
        archiveTask.description = "Bundles the project as a JVM application with libs and OS specific scripts."
        archiveTask.group = ApplicationPlugin.APPLICATION_GROUP
        archiveTask.conventionMapping.baseName = { pluginConvention.applicationName }
        def baseDir = { archiveTask.archiveName - ".${archiveTask.extension}" }
        archiveTask.into(baseDir) {
            with(extension.applicationDistribution)
        }
    }

    protected CopySpec configureDistSpec(Project project, CopySpec distSpec) {
        def startScripts = project.tasks.startShadowScripts

        distSpec.with {
            from(project.file("src/dist"))

            into("lib") {
                from(jar)
                from(project.configurations.shadow)
            }
            into("bin") {
                from(startScripts)
                fileMode = 0755
            }
        }

        distSpec
    }

    private ShadowJar getJar() {
        project.tasks.findByName(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME)
    }
}
