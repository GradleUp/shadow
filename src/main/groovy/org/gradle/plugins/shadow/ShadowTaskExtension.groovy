package org.gradle.plugins.shadow

import org.gradle.api.Project

class ShadowTaskExtension {

    public static final NAME = "shadow"

    List<String> includes = []
    List<String> excludes = []
    String destinationDir = "${project.buildDir}/libs"
    String baseName = "${project.archivesBaseName}-shadow-${project.version}"
    String extension = "jar"

    private final Project project

    ShadowTaskExtension(Project project) {
        this.project = project
    }

    File getShadowJar() {
        return new File(destinationDir, "$baseName.$extension")
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
