package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.internal.StartScriptGenerator
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GUtil

class ShadowCreateStartScripts extends ConventionTask {
    /**
     * The directory to write the scripts into.
     */
    File outputDir

    /**
     * The application's default JVM options.
     */
    @Input
    @Optional
    Iterable<String> defaultJvmOpts = []

    /**
     * The application's name.
     */
    @Input
    String applicationName

    String optsEnvironmentVar

    String exitEnvironmentVar

    /**
     * The main application jar.
     */
    @InputFile
    File mainApplicationJar

    /**
     * Returns the name of the application's OPTS environment variable.
     */
    @Input
    String getOptsEnvironmentVar() {
        if (optsEnvironmentVar) {
            return optsEnvironmentVar
        }
        if (!getApplicationName()) {
            return null
        }
        return "${GUtil.toConstant(getApplicationName())}_OPTS"
    }

    @Input
    String getExitEnvironmentVar() {
        if (exitEnvironmentVar) {
            return exitEnvironmentVar
        }
        if (!getApplicationName()) {
            return null
        }
        return "${GUtil.toConstant(getApplicationName())}_EXIT_CONSOLE"
    }

    @OutputFile
    File getUnixScript() {
        return new File(getOutputDir(), getApplicationName())
    }

    @OutputFile
    File getWindowsScript() {
        return new File(getOutputDir(), "${getApplicationName()}.bat")
    }

    @TaskAction
    void generate() {
        def generator = new StartScriptGenerator()
        generator.applicationName = getApplicationName()
        generator.mainApplicationJar = "libs/${getMainApplicationJar().name}"
        generator.defaultJvmOpts = getDefaultJvmOpts()
        generator.optsEnvironmentVar = getOptsEnvironmentVar()
        generator.exitEnvironmentVar = getExitEnvironmentVar()
        generator.scriptRelPath = "bin/${getUnixScript().name}"
        generator.generateUnixScript(getUnixScript())
        generator.generateWindowsScript(getWindowsScript())
    }
}
