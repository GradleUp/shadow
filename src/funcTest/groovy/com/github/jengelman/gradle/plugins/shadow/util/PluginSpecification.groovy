package com.github.jengelman.gradle.plugins.shadow.util

import org.codehaus.plexus.util.IOUtil
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.function.Function
import java.util.jar.JarEntry
import java.util.jar.JarFile

abstract class PluginSpecification extends Specification {

    @TempDir
    Path dir

    public static final String SHADOW_VERSION = System.getProperty("shadowVersion")

    AppendableMavenFileRepository repo

    def setup() {
        repo = repo()
        repo.module('junit', 'junit', '3.8.2').use(testJar).publish()

        buildFile << defaultBuildScript

        settingsFile << '''
            rootProject.name = 'shadow'
        '''
    }

    def cleanup() {
        println buildFile.text
    }

    String getDefaultBuildScript(String javaPlugin = 'java') {
        return """
        plugins {
            id '${javaPlugin}'
            id 'com.gradleup.shadow'
        }

        version = "1.0"
        group = 'shadow'

        sourceSets {
          integTest
        }

        repositories { maven { url "${repo.uri}" } }
        """.stripIndent()
    }

    GradleRunner getRunner() {
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .forwardOutput()
            .withPluginClasspath()
            .withTestKitDir(testKitDir)
    }

    GradleRunner runner(Collection<String> tasks) {
        runner.withArguments(["-Dorg.gradle.warning.mode=all", "--configuration-cache", "--stacktrace"] + tasks.toList())
    }

    BuildResult run(String... tasks) {
        run(tasks.toList())
    }

    BuildResult run(List<String> tasks, Function<GradleRunner, GradleRunner> runnerFunction = { it }) {
        def result = runnerFunction.apply(runner(tasks)).build()
        assertNoDeprecationWarnings(result)
        return result
    }

    BuildResult runWithDebug(String... tasks) {
        return run(tasks.toList(), { it.withDebug(true) })
    }

    BuildResult runWithFailure(List<String> tasks, Function<GradleRunner, GradleRunner> runnerFunction = { it }) {
        def result = runnerFunction.apply(runner(tasks)).buildAndFail()
        assertNoDeprecationWarnings(result)
        return result
    }

    static void assertNoDeprecationWarnings(BuildResult result) {
        result.output.eachLine {
            assert !containsDeprecationWarning(it)
        }
    }

    static boolean containsDeprecationWarning(String output) {
        output.contains("has been deprecated and is scheduled to be removed in Gradle") ||
            output.contains("has been deprecated. This is scheduled to be removed in Gradle")
    }

    File getBuildFile() {
        file('build.gradle')
    }

    File getSettingsFile() {
        file('settings.gradle')
    }

    File file(String path) {
        File f = dir.resolve(path).toFile()
        if (!f.exists()) {
            f.parentFile.mkdirs()
            if (!f.createNewFile()) {
                throw new IOException("a file with the name \'" + f.name + "\' already exists in the test folder.")
            }
        }
        return f
    }

    File getFile(String path) {
        return dir.resolve(path).toFile()
    }

    AppendableMavenFileRepository repo(String path = 'maven-repo') {
        new AppendableMavenFileRepository(dir.resolve(path).toFile())
    }

    void assertJarFileContentsEqual(File f, String path, String contents) {
        assert getJarFileContents(f, path) == contents
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

    void contains(File f, List<String> paths) {
        JarFile jar = new JarFile(f)
        paths.each { path ->
            assert jar.getJarEntry(path), "${f.path} does not contain [$path]"
        }
        jar.close()
    }

    void doesNotContain(File f, List<String> paths) {
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
        return new AppendableJar(file(path))
    }

    protected File getOutput() {
        getFile('build/libs/shadow-1.0-all.jar')
    }

    protected File output(String name) {
        getFile("build/libs/${name}")
    }

    protected File getTestJar(String name = 'junit-3.8.2.jar') {
        return new File(this.class.classLoader.getResource(name).toURI())
    }

    protected static File getTestKitDir() {
        def gradleUserHome = System.getenv("GRADLE_USER_HOME")
        if (!gradleUserHome) {
            gradleUserHome = new File(System.getProperty("user.home"), ".gradle").absolutePath
        }
        return new File(gradleUserHome, "testkit")
    }

    /**
     * TODO: this is used as extensions for Groovy, could be replaced after migrated to Kotlin.
     *  Registered in resources/META-INF/services/org.codehaus.groovy.runtime.ExtensionModule.
     */
    static final class FileExtensions {
        static final File resolve(File file, String relativePath) {
            try {
                return new File(file, relativePath)
            } catch (RuntimeException e) {
                throw new RuntimeException(String.format("Could not locate file '%s' relative to '%s'.", Arrays.toString(relativePath), file), e)
            }
        }

        static final File createDir(File file) {
            if (file.mkdirs()) {
                return file
            }
            if (file.isDirectory()) {
                return file
            }
            throw new AssertionError("Problems creating dir: " + this
                + ". Diagnostics: exists=" + file.exists() + ", isFile=" + file.isFile() + ", isDirectory=" + file.isDirectory())
        }
    }
}
