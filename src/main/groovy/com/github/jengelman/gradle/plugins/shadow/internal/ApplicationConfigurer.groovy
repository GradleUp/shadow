package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip

class ApplicationConfigurer {

    private final Jar jar

    ApplicationConfigurer(Jar jar) {
        assert jar
        this.jar = jar
    }

    public void execute(Project project) {
        addRunTask(project)
        addCreateScriptsTask(project)

        configureDistSpec(project, project.extensions.shadow.applicationDistribution)

        addInstallTask(project)
        addDistZipTask(project)
        addDistTarTask(project)
    }

    private void addRunTask(Project project) {
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application

        def run = project.tasks.create('runShadow', JavaExec)
        run.description  = 'Runs this project as a JVM application using the shadow jar'
        run.group = ApplicationPlugin.APPLICATION_GROUP
        run.classpath = jar.outputs.files + project.configurations.shadow
        run.conventionMapping.main = { pluginConvention.mainClassName }
        run.conventionMapping.jvmArgs = { pluginConvention.applicationDefaultJvmArgs }
    }

    private void addCreateScriptsTask(Project project) {
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application

        def startScripts = project.tasks.create('startShadowScripts', CreateStartScripts)
        startScripts.description = 'Creates OS specific scripts to run the project as a JVM application using the shadow jar'
        startScripts.group = ApplicationPlugin.APPLICATION_GROUP
        startScripts.classpath = project.tasks.shadowJar.outputs.files + project.configurations.shadow
        startScripts.conventionMapping.mainClassName = { pluginConvention.mainClassName }
        startScripts.conventionMapping.applicationName = { pluginConvention.applicationName }
        startScripts.conventionMapping.outputDir = { new File(project.buildDir, 'scriptsShadow') }
        startScripts.conventionMapping.defaultJvmOpts = { pluginConvention.applicationDefaultJvmArgs }
    }

    private void addInstallTask(Project project) {
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application

        def installTask = project.tasks.create('installShadowApp', Sync)
        installTask.description = "Installs the project as a JVM application along with libs and OS specific scripts."
        installTask.group = ApplicationPlugin.APPLICATION_GROUP
        installTask.with project.extensions.shadow.applicationDistribution
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

    private void addDistZipTask(Project project) {
        addArchiveTask(project, 'distShadowZip', Zip)
    }

    private void addDistTarTask(Project project) {
        addArchiveTask(project, 'distShadowTar', Tar)
    }

    private <T extends AbstractArchiveTask> void addArchiveTask(Project project, String name, Class<T> type) {
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application

        def archiveTask = project.tasks.create(name, type)
        archiveTask.description = "Bundles the project as a JVM application with libs and OS specific scripts."
        archiveTask.group = ApplicationPlugin.APPLICATION_GROUP
        archiveTask.conventionMapping.baseName = { pluginConvention.applicationName }
        def baseDir = { archiveTask.archiveName - ".${archiveTask.extension}" }
        archiveTask.into(baseDir) {
            with(pluginConvention.applicationDistribution)
        }
    }

    private CopySpec configureDistSpec(Project project, CopySpec distSpec) {
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

}
