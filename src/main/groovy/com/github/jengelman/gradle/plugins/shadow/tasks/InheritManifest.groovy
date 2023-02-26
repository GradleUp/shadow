package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.Project
import org.gradle.api.java.archives.Manifest

interface InheritManifest extends Manifest {

    InheritManifest inheritFrom(Project project, Object... inheritPaths)

    InheritManifest inheritFrom(Project project, Object inheritPaths, Closure closure)
}
