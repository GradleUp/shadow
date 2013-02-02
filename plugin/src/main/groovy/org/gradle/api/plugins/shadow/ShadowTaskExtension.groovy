package org.gradle.api.plugins.shadow

import org.gradle.api.Project

class ShadowTaskExtension {

    public static final NAME = "shadow"

    List<String> includes = []
    List<String> excludes = ['META-INF/INDEX.LIST']
    String destinationDir = "${project.buildDir}/libs/"
    String baseName = "${project.archivesBaseName}-shadow-${project.version}"
    String extension = "jar"
    boolean stats = false
    boolean artifactAttached = true

    private final Project project

    ShadowTaskExtension(Project project) {
        this.project = project
    }

    File getShadowJar() {
        return new File(destinationDir, "$baseName.$extension")
    }

    String getSignedLibsDir() {
        return destinationDir + 'signedLibs/'
    }

    ShadowTaskExtension exclude(String s) {
        excludes << s
        this
    }

    ShadowTaskExtension include(String s) {
        includes << s
        this
    }

}
