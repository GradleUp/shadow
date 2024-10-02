package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.Action
import org.gradle.api.java.archives.Manifest

public interface InheritManifest : Manifest {
    public fun inheritFrom(vararg inheritPaths: Any): InheritManifest
    public fun inheritFrom(vararg inheritPaths: Any, action: Action<InheritManifest>?): InheritManifest
}
