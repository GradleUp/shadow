package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.java.archives.Manifest

/**
 * @deprecated Use {@link Manifest#from(Object...)} on the standard Gradle {@link Manifest} instead.
 */
@Deprecated
interface InheritManifest extends Manifest {

    InheritManifest inheritFrom(Object... inheritPaths)

    InheritManifest inheritFrom(inheritPaths, Closure closure)
}
