package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.Action
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestMergeSpec

public interface InheritManifest : Manifest {
  public fun inheritFrom(vararg inheritPaths: Any) {
    inheritFrom(inheritPaths = inheritPaths, action = {})
  }

  public fun inheritFrom(vararg inheritPaths: Any, action: Action<ManifestMergeSpec>)
}
