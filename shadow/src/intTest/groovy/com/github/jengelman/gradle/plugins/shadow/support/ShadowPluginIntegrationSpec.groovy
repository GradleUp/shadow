package com.github.jengelman.gradle.plugins.shadow.support

import com.github.jengelman.gradle.plugins.integration.PluginIntegrationSpec
import org.gradle.mvn3.org.codehaus.plexus.util.IOUtil

import java.util.jar.JarEntry
import java.util.jar.JarFile

abstract class ShadowPluginIntegrationSpec extends PluginIntegrationSpec {

    def setup() {
        applyPlugin('shadow')
        settingsFile << 'rootProject.name=\'shadowTest\''
    }

    @Override
    public AppendableMavenFileRepository getMavenRepo() {
        if (this.@mavenRepo == null) {
            this.@mavenRepo = new AppendableMavenFileRepository(file("maven-repo"))
        }
        return this.@mavenRepo
    }

    File getShadowOutput() {
        file('build/distributions/shadowTest-unspecified.jar')
    }

    void assertJarFileContentsEqual(File f, String path, String contents) {
        JarFile jf = new JarFile(f)
        println jf.entries()*.name
        def is = jf.getInputStream(new JarEntry(path))
        StringWriter sw = new StringWriter()
        IOUtil.copy(is, sw)
        is.close()
        assert sw.toString() == contents
    }

}
