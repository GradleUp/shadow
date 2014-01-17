package com.github.jengelman.gradle.plugins.integration

import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.junit.Rule
import spock.lang.Specification

abstract class PluginIntegrationSpec extends Specification implements TestDirectoryProvider {

    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    protected MavenFileRepository mavenRepo

    ProjectConnection project
    Results results

    def setup() {
        project = GradleConnector.newConnector()
                .forProjectDirectory(testDirectory)
                .useGradleUserHomeDir(homeDirectory)
                .connect()
        addPluginInit()
        results = new Results()
    }

    def cleanup() {
        project.close()
    }

    protected BuildLauncher execute(String... tasks) {
        runFile << 'buildscript {\n'
        runFile << pluginInitFile.text + '\n'
        runFile << '}\n'
        runFile << buildFile.text
        return project.newBuild().forTasks(tasks).run(results)
    }

    protected TestFile getPluginInitFile() {
        initDirectory.file('plugin.gradle')
    }

    protected TestFile getInitDirectory() {
        homeDirectory.createDir('test-kit')
    }

    protected TestFile getHomeDirectory() {
        testDirectory.createDir('gradle-home')
    }

    protected TestFile getRunFile() {
        file('build.gradle')
    }

    protected TestFile getBuildFile() {
        file('testBuild.gradle')
    }

    protected TestFile getSettingsFile() {
        file('settings.gradle')
    }

    public TestFile getTestDirectory() {
        temporaryFolder.testDirectory
    }

    protected void addPluginInit() {
        pluginInitFile << 'dependencies {\n'
        runtimePaths.each {
            pluginInitFile << "classpath files('$it')\n"
        }
        pluginInitFile << '}\n'
    }

    protected List<String> getRuntimePaths() {
        System.getProperty('runtime.jars.path', '').split(',')
    }

    protected void applyPlugin(Class plugin) {
        buildFile << "apply plugin: ${plugin.name}\n"
    }

    protected void applyPlugin(String plugin) {
        buildFile << "apply plugin: '$plugin'\n"
    }

    protected void buildSuccessful() {
        results.waitForCompletion()
        if (!results.successful) {
            if (!results.failed) {
                throw new AssertionError('Gradle build was not executed.')
            }
            throw new AssertionError('Gradle build failed with error')
        }
    }

    public MavenFileRepository getMavenRepo() {
        if (mavenRepo == null) {
            mavenRepo = new MavenFileRepository(file("maven-repo"))
        }
        return mavenRepo
    }

    protected TestFile file(Object... path) {
        testDirectory.file(path)
    }
}
