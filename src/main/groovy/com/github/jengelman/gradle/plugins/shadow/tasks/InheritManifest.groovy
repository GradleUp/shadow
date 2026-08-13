package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.java.archives.Manifest

/**
 * @deprecated inheritFrom should be replaced by from.
 */
@Deprecated
interface InheritManifest extends Manifest {

    InheritManifest inheritFrom(Object... inheritPaths)

    InheritManifest inheritFrom(inheritPaths, Closure closure)
}
