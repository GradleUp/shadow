package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.Action
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestMergeSpec

@Deprecated(
  message = "Use `Manifest` instead. This will be removed in Shadow 10.",
  replaceWith = ReplaceWith("Manifest", "org.gradle.api.java.archives.Manifest"),
)
public interface InheritManifest : Manifest {
  @Deprecated(
    message = "Use `from` instead. This will be removed in Shadow 10.",
    replaceWith = ReplaceWith("from"),
  )
  public fun inheritFrom(vararg inheritPaths: Any) {
    @Suppress("DEPRECATION") inheritFrom(inheritPaths = inheritPaths, action = {})
  }

  @Deprecated(
    "Use `from` instead. This will be removed in Shadow 10.",
    replaceWith = ReplaceWith("from"),
  )
  public fun inheritFrom(vararg inheritPaths: Any, action: Action<ManifestMergeSpec>)
}
