package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome


class AbstractCachingSpec extends PluginSpecification {
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

    private String escapedPath(File file) {
        file.path.replaceAll('\\\\', '\\\\\\\\')
    }

    void assertShadowJarResult(TaskOutcome expectedOutcome) {
        def result = runWithCacheEnabled("shadowJar")
        assert result.task(':shadowJar').outcome == expectedOutcome
    }

    void assertShadowJarCached() {
        deleteOutputs()
        assertShadowJarResult(TaskOutcome.FROM_CACHE)
    }

    void assertShadowJarNotCached() {
        deleteOutputs()
        assertShadowJarResult(TaskOutcome.SUCCESS)
    }

    void deleteOutputs() {
        if (output.exists()) {
            assert output.delete()
        }
    }
}
