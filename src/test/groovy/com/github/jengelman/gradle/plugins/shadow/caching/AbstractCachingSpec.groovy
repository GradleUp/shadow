package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS


class AbstractCachingSpec extends PluginSpecification {
    @Rule TemporaryFolder alternateDir

    def setup() {
        // Use a test-specific build cache directory.  This ensures that we'll only use cached outputs generated during this
        // test and we won't accidentally use cached outputs from a different test or a different build.
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = new File(rootDir, 'build-cache')
                }
            }
        """
    }

    void changeConfigurationTo(String content) {
        buildFile.text = defaultBuildScript
        buildFile << content
    }

    BuildResult runWithCacheEnabled(String... arguments) {
        List<String> cacheArguments = [ '--build-cache' ]
        cacheArguments.addAll(arguments)
        return runner.withArguments(cacheArguments).build()
    }

    BuildResult runInAlternateDirWithCacheEnabled(String... arguments) {
        List<String> cacheArguments = [ '--build-cache' ]
        cacheArguments.addAll(arguments)
        return alternateDirRunner.withArguments(cacheArguments).build()
    }

    GradleRunner getAlternateDirRunner() {
        GradleRunner.create()
                .withProjectDir(alternateDir.root)
                .forwardOutput()
                .withPluginClasspath()
    }

    private String escapedPath(File file) {
        file.path.replaceAll('\\\\', '\\\\\\\\')
    }

    void assertShadowJarHasResult(TaskOutcome expectedOutcome) {
        def result = runWithCacheEnabled("shadowJar")
        assert result.task(':shadowJar').outcome == expectedOutcome
    }

    void assertShadowJarHasResultInAlternateDir(TaskOutcome expectedOutcome) {
        def result = runInAlternateDirWithCacheEnabled("shadowJar")
        assert result.task(':shadowJar').outcome == expectedOutcome
    }

    void copyToAlternateDir() {
        FileUtils.deleteDirectory(alternateDir.root)
        FileUtils.forceMkdir(alternateDir.root)
        FileUtils.copyDirectory(dir.root, alternateDir.root)
    }

    void assertShadowJarIsCachedAndRelocatable() {
        deleteOutputs()
        copyToAlternateDir()
        // check that shadowJar pulls from cache in the original directory
        assertShadowJarHasResult(FROM_CACHE)
        // check that shadowJar pulls from cache in a different directory
        assertShadowJarHasResultInAlternateDir(FROM_CACHE)
    }

    void assertShadowJarExecutes() {
        deleteOutputs()
        // task was executed and not pulled from cache
        assertShadowJarHasResult(SUCCESS)
    }

    void deleteOutputs() {
        if (output.exists()) {
            assert output.delete()
        }
    }
}
