package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.testkit.GradleRunnerFactory
import com.github.jengelman.gradle.testkit.file.TestFile
import org.codehaus.plexus.util.IOUtil
import org.gradle.testkit.functional.ExecutionResult
import org.gradle.testkit.functional.GradleRunner
import org.gradle.tooling.GradleConnector
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarFile

class PluginSpecification extends Specification {

    @Rule TemporaryFolder dir
    GradleRunner runner

    AppendableMavenFileRepository repo

    def setup() {
        runner = GradleRunnerFactory.create({
            daemonMaxIdleTime(10, TimeUnit.SECONDS)
        }, {
            setJvmArguments('-Xmx128m')
        })
        runner.directory = dir.root
        repo = repo()
        repo.module('junit', 'junit', '3.8.2').use(testJar).publish()
    }

    def cleanup() {
        runner.close()
    }

    File getBuildFile() {
        file('build.gradle')
    }

    File getSettingsFile() {
        file('settings.gradle')
    }

    File file(String path) {
        File f = new File(dir.root, path)
        if (!f.exists()) {
            f.parentFile.mkdirs()
            return dir.newFile(path)
        }
        return f
    }

    AppendableMavenFileRepository repo(String path = 'maven-repo') {
        new AppendableMavenFileRepository(new TestFile(dir.root, path))
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

    AppendableJar buildJar(String path) {
        return new AppendableJar(file(path))
    }

    void success(ExecutionResult result) {
        assert result.standardOutput.contains('BUILD SUCCESSFUL'), 'Gradle build failed with error'
    }

    void fail(ExecutionResult result) {
        assert result.standardError, 'Gradle build succeeded'
    }

    boolean taskUpToDate(ExecutionResult result, String taskName) {
        result.standardOutput.find(/:${taskName}(.*)/).trim().contains('UP-TO-DATE')
    }

    protected getOutput() {
        file('build/libs/shadow.jar')
    }

    protected File getTestJar(String name = 'junit-3.8.2.jar') {
        return new File(this.class.classLoader.getResource(name).toURI())
    }
}
