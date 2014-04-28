package com.github.jengelman.gradle.plugins.shadow2.util

import org.gradle.testkit.functional.GradleRunner
import org.gradle.testkit.functional.GradleRunnerFactory
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class PluginSpecification extends Specification {

    @Rule TemporaryFolder dir
    GradleRunner runner

    def setup() {
        runner = GradleRunnerFactory.create()
        runner.directory = dir.root
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
}
