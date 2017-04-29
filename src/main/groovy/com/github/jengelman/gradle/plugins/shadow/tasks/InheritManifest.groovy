package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.java.archives.Manifest

interface InheritManifest extends Manifest {

    InheritManifest inheritFrom(Object... inheritPaths)

    InheritManifest inheritFrom(Object inheritPaths, Closure closure)
}
