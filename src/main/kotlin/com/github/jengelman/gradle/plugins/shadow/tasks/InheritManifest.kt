package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.Action
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestMergeSpec

@Deprecated(
  message = "This is deprecated and will be removed in a future release.",
  replaceWith = ReplaceWith("Manifest", "org.gradle.api.java.archives.Manifest"),
)
public interface InheritManifest : Manifest {
  @Deprecated(
    message = "This is deprecated and will be removed in a future release.",
    replaceWith = ReplaceWith("from"),
  )
  public fun inheritFrom(vararg inheritPaths: Any) {
    inheritFrom(inheritPaths = inheritPaths, action = {})
  }

  public fun inheritFrom(vararg inheritPaths: Any, action: Action<ManifestMergeSpec>)
}
