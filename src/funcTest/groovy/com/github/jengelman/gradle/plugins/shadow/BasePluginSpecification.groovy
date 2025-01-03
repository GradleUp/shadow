package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.util.AppendableJar
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import org.apache.commons.lang3.StringUtils
import org.codehaus.plexus.util.IOUtil
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function
import java.util.jar.JarEntry
import java.util.jar.JarFile

abstract class BasePluginSpecification extends Specification {

    @TempDir
    Path root

    AppendableMavenFileRepository repo

    def setup() {
        repo = repo()
        repo.module('junit', 'junit', '3.8.2')
            .use(Paths.get(this.class.classLoader.getResource('junit-3.8.2.jar').toURI()))
            .publish()

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
                maven { url = "${repo.uri}" }
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

    BuildResult run(String... tasks) {
        run(tasks.toList())
    }

    BuildResult run(List<String> tasks, Function<GradleRunner, GradleRunner> runnerFunction = { it }) {
        def result = runnerFunction.apply(runner(tasks)).build()
        assertNoDeprecationWarnings(result)
        return result
    }

    BuildResult runWithFailure(List<String> tasks, Function<GradleRunner, GradleRunner> runnerFunction = { it }) {
        def result = runnerFunction.apply(runner(tasks)).buildAndFail()
        assertNoDeprecationWarnings(result)
        return result
    }

    private static void assertNoDeprecationWarnings(BuildResult result) {
        result.output.eachLine {
            assert !containsDeprecationWarning(it)
        }
    }

    private static boolean containsDeprecationWarning(String output) {
        output.contains("has been deprecated and is scheduled to be removed in Gradle") ||
            output.contains("has been deprecated. This is scheduled to be removed in Gradle")
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

    AppendableMavenFileRepository repo(String path = 'maven-repo') {
        new AppendableMavenFileRepository(root.resolve(path))
    }

    String getJarFileContents(File f, String path) {
        JarFile jf = new JarFile(f)
        def is = jf.getInputStream(new JarEntry(path))
        StringWriter sw = new StringWriter()
        IOUtil.copy(is, sw)
        is.close()
        jf.close()
        return sw.toString()
    }

    void assertContains(File f, List<String> paths) {
        JarFile jar = new JarFile(f)
        paths.each { path ->
            assert jar.getJarEntry(path), "${f.path} does not contain [$path]"
        }
        jar.close()
    }

    void assertDoesNotContain(File f, List<String> paths) {
        JarFile jar = new JarFile(f)
        paths.each { path ->
            assert !jar.getJarEntry(path), "${f.path} contains [$path]"
        }
        jar.close()
    }

    /**
     * Helper method to allow scoping variables into a closure in a spock test
     * Prevents variable expansion
     * When using this you *must* include explicit `assert` statements as Spock will not do it for you
     */
    void assertions(Closure closure) {
        closure()
    }

    AppendableJar buildJar(String path) {
        return new AppendableJar(file(path).toPath())
    }

    File getOutputShadowJar() {
        file('build/libs/shadow-1.0-all.jar')
    }

    static File getTestKitDir() {
        def gradleUserHome = System.getenv("GRADLE_USER_HOME")
        if (!gradleUserHome) {
            gradleUserHome = new File(System.getProperty("user.home"), ".gradle").absolutePath
        }
        return new File(gradleUserHome, "testkit")
    }

    static String escapedPath(Path path) {
        return path.toString().replaceAll('\\\\', '\\\\\\\\')
    }
}
