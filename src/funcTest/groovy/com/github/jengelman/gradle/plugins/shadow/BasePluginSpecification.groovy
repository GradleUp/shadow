package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenRepository
import org.apache.commons.lang3.StringUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile

abstract class BasePluginSpecification extends Specification {

    @TempDir
    Path root

    AppendableMavenRepository repo

    def setup() {
        repo = new AppendableMavenRepository(root.resolve('local-maven-repo'), runner)
        repo.module('junit', 'junit', '3.8.2') { module ->
            module.useJar(Paths.get(this.class.classLoader.getResource('junit-3.8.2.jar').toURI()))
        }.publish()

        projectScriptFile << getDefaultProjectBuildScript('java', true, true)
        settingsScriptFile << getDefaultSettingsBuildScript()
    }

    def cleanup() {
        println projectScriptFile.text
    }

    String getDefaultProjectBuildScript(
        String javaPlugin = 'java',
        boolean withGroup = false,
        boolean withVersion = false
    ) {
        def groupInfo = withGroup ? "group = 'shadow'" : ""
        def versionInfo = withVersion ? "version = '1.0'" : ""

        return """
        plugins {
            id '${javaPlugin}'
            id 'com.gradleup.shadow'
        }

        $groupInfo
        $versionInfo
        """.stripIndent().trim() + System.lineSeparator()
    }

    String getDefaultSettingsBuildScript(boolean withRootProject = true) {
        def rootProjectInfo = withRootProject ? "rootProject.name = 'shadow'" : ""
        return """
            dependencyResolutionManagement {
              repositories {
                maven { url = "${repo.root.toUri()}" }
                mavenCentral()
              }
            }

            $rootProjectInfo
        """.stripIndent().trim() + System.lineSeparator()
    }

    static def shadowJar = "tasks.named('shadowJar', ${ShadowJar.class.name})".trim()

    GradleRunner getRunner() {
        GradleRunner.create()
            .withProjectDir(root.toFile())
            .forwardOutput()
            .withPluginClasspath()
            .withTestKitDir(testKitDir)
    }

    GradleRunner runner(Collection<String> tasks) {
        runner.withArguments(["--warning-mode=fail", "--configuration-cache", "--stacktrace"] + tasks.toList())
    }

    BuildResult run(List<String> tasks) {
        def result = runner(tasks).build()
        result.output.eachLine { output ->
            assert !(
                output.contains("has been deprecated and is scheduled to be removed in Gradle") ||
                    output.contains("has been deprecated. This is scheduled to be removed in Gradle")
            )
        }
        return result
    }

    File getProjectScriptFile() {
        file('build.gradle')
    }

    File getSettingsScriptFile() {
        file('settings.gradle')
    }

    File file(String path) {
        File f = root.resolve(path).toFile()
        String extension = StringUtils.substringAfterLast(path, '.')

        // Binary files should be asserted to exist, text files should be created.
        if (extension == "jar" || extension == "zip") {
            return f
        }

        if (!f.exists()) {
            f.parentFile.mkdirs()
            if (!f.createNewFile()) {
                throw new IOException("a file with the name \'" + f.name + "\' already exists in the test folder.")
            }
        }
        return f
    }

    void containsEntries(File f, List<String> paths) {
        JarFile jar = new JarFile(f)
        paths.each { path ->
            assert jar.getJarEntry(path), "${f.path} does not contain [$path]"
        }
        jar.close()
    }

    void doesNotContainEntries(File f, List<String> paths) {
        JarFile jar = new JarFile(f)
        paths.each { path ->
            assert !jar.getJarEntry(path), "${f.path} contains [$path]"
        }
        jar.close()
    }

    File getOutputShadowJar() {
        file('build/libs/shadow-1.0-all.jar')
    }

    private static File getTestKitDir() {
        def gradleUserHome = System.getenv("GRADLE_USER_HOME")
        if (!gradleUserHome) {
            gradleUserHome = new File(System.getProperty("user.home"), ".gradle").absolutePath
        }
        return new File(gradleUserHome, "testkit")
    }
}
