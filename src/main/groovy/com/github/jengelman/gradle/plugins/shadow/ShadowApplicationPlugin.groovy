package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.toolchain.JavaToolchainService

/**
 * A {@link Plugin} which packages and runs a project as a Java Application using the shadowed jar.
 *
 * Modified from
 * <a href="https://github.com/gradle/gradle/blob/fdecc3c95828bb9a1c1bb6114483fe5b16f9159d/platforms/jvm/plugins-application/src/main/java/org/gradle/api/plugins/ApplicationPlugin.java">org.gradle.api.plugins.ApplicationPlugin.java</a>.
 *
 * @see ApplicationPlugin
 */
abstract class ShadowApplicationPlugin implements Plugin<Project> {

    public static final String SHADOW_RUN_TASK_NAME = 'runShadow'
    public static final String SHADOW_SCRIPTS_TASK_NAME = 'startShadowScripts'
    public static final String SHADOW_INSTALL_TASK_NAME = 'installShadowDist'

    private static final String DISTRIBUTION_NAME = ShadowBasePlugin.EXTENSION_NAME

    @Override
    void apply(Project project) {
        addRunTask(project)
        addCreateScriptsTask(project)
        configureDistribution(project)
        configureJarMainClass(project)
        configureInstallTask(project)
    }

    protected void addRunTask(Project project) {
        project.tasks.register(SHADOW_RUN_TASK_NAME, JavaExec) { task ->
            task.description = "Runs this project as a JVM application using the shadow jar"
            task.group = ApplicationPlugin.APPLICATION_GROUP

            task.classpath = project.files(project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME))

            def applicationExtension = project.extensions.getByType(JavaApplication)
            def javaPluginExtension = project.extensions.getByType(JavaPluginExtension)
            def javaToolchainService = project.extensions.getByType(JavaToolchainService)

            task.mainModule.convention(applicationExtension.mainModule)
            task.mainClass.convention(applicationExtension.mainClass)
            task.jvmArguments.convention(project.provider { applicationExtension.applicationDefaultJvmArgs })

            task.modularity.inferModulePath.convention(javaPluginExtension.modularity.inferModulePath)
            task.javaLauncher.convention(javaToolchainService.launcherFor(javaPluginExtension.toolchain))
        }
    }

    protected void addCreateScriptsTask(Project project) {
        project.tasks.register(SHADOW_SCRIPTS_TASK_NAME, CreateStartScripts) { task ->
            task.description = "Creates OS specific scripts to run the project as a JVM application using the shadow jar"

            task.classpath = project.files(project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME))

            def applicationExtension = project.extensions.getByType(JavaApplication)
            def javaPluginExtension = project.extensions.getByType(JavaPluginExtension)

            // TODO: replace usages of conventionMapping.
            task.mainModule.convention(applicationExtension.mainModule)
            task.mainClass.convention(applicationExtension.mainClass)
            task.conventionMapping.map("applicationName") { applicationExtension.applicationName }
            task.conventionMapping.map("outputDir") {
                project.layout.buildDirectory.dir("scriptsShadow").get().asFile
            }
            task.conventionMapping.map("executableDir") { applicationExtension.executableDir }
            task.conventionMapping.map("defaultJvmOpts") { applicationExtension.applicationDefaultJvmArgs }

            task.modularity.inferModulePath.convention(javaPluginExtension.modularity.inferModulePath)
        }
    }

    protected void configureInstallTask(Project project) {
        project.tasks.named(SHADOW_INSTALL_TASK_NAME, Sync).configure { task ->
            def applicationExtension = project.extensions.getByType(JavaApplication)
            def applicationName = project.provider { applicationExtension.applicationName }
            def executableDir = project.provider { applicationExtension.executableDir }

            task.doFirst("Check installation directory") {
                def destinationDir = task.destinationDir
                def children = destinationDir.list()
                if (children == null) {
                    throw new IOException("Could not list directory ${destinationDir}")
                }
                if (children.length == 0) return
                if (!new File(destinationDir, "lib").isDirectory() ||
                    !new File(destinationDir, executableDir.get()).isDirectory()) {
                    throw new GradleException(
                        "The specified installation directory '${destinationDir}' is neither empty nor does it contain an installation for '${applicationName.get()}'.\n" +
                            "If you really want to install to this directory, delete it and run the install task again.\n" +
                            "Alternatively, choose a different installation directory."
                    )
                }
            }
        }
    }

    protected void configureDistribution(Project project) {
        def distributions = project.extensions.getByType(DistributionContainer)
        distributions.register(DISTRIBUTION_NAME) { dist ->
            def applicationExtension = project.extensions.getByType(JavaApplication)
            dist.distributionBaseName.convention(
                project.provider {
                    // distributionBaseName defaults to `$project.name-$distribution.name`, applicationName
                    // defaults to project.name
                    // so we append the suffix to match the default distributionBaseName. Modified from
                    // `ApplicationPlugin.configureDistribution()`.
                    "${applicationExtension.applicationName}-${DISTRIBUTION_NAME}"
                }
            )
            dist.contents { distSpec ->
                distSpec.from(project.file("src/dist"))
                distSpec.into("lib") { lib ->
                    lib.from(project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME))
                    // Reflects the value of the `Class-Path` attribute in the JAR manifest.
                    lib.from(project.configurations.named(ShadowBasePlugin.CONFIGURATION_NAME))
                }
                // Defaults to bin dir.
                distSpec.into(project.provider { applicationExtension.executableDir }) { bin ->
                    bin.from(project.tasks.named(SHADOW_SCRIPTS_TASK_NAME))
                    bin.filePermissions { permissions -> permissions.unix('rwxr-xr-x') }
                }
                distSpec.with(applicationExtension.applicationDistribution)
                configureDistSpec(project, distSpec)
            }
        }
    }

    protected CopySpec configureDistSpec(Project project, CopySpec distSpec) {
        return distSpec
    }

    protected void configureJarMainClass(Project project) {
        def applicationExtension = project.extensions.getByType(JavaApplication)
        project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME, ShadowJar).configure { task ->
            task.inputs.property('mainClassName', applicationExtension.mainClass)
            task.doFirst {
                task.manifest.attributes 'Main-Class': applicationExtension.mainClass.get()
            }
        }
    }
}
