package com.github.jengelman.gradle.plugins.shadow.support

import com.github.jengelman.gradle.plugins.integration.PluginIntegrationSpec
import org.gradle.mvn3.org.codehaus.plexus.util.IOUtil

import java.util.jar.JarEntry
import java.util.jar.JarFile

abstract class ShadowPluginIntegrationSpec extends PluginIntegrationSpec {

    def setup() {
        applyPlugin('shadow')
        settingsFile << 'rootProject.name=\'shadowTest\''
        buildFile << 'version=0.1'
    }

    @Override
    public AppendableMavenFileRepository getMavenRepo() {
        if (this.@mavenRepo == null) {
            this.@mavenRepo = new AppendableMavenFileRepository(file("maven-repo"))
        }
        return this.@mavenRepo
    }

    File getShadowOutput() {
        file('build/distributions/shadowTest-0.1.jar')
    }

    File shadowOutput(String classifier) {
        file("build/distributions/shadowTest-0.1-${classifier}.jar")
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
        return sw.toString()
    }

    void contains(File f, List<String> paths) {
        JarFile jar = new JarFile(f)
        paths.each { path ->
            assert jar.getJarEntry(path), "${f.path} does not contain [$path]"
        }
    }

    void contains(List<String> paths) {
        contains(shadowOutput, paths)
    }

    void doesNotContain(File f, List<String> paths) {
        JarFile jar = new JarFile(f)
        paths.each { path ->
            assert !jar.getJarEntry(path), "${f.path} contains [$path]"
        }
    }

    void doesNotContain(List<String> paths) {
        doesNotContain(shadowOutput, paths)
    }



}
