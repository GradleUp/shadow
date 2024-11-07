package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.Action
import org.gradle.api.java.archives.Manifest

public interface InheritManifest <T : Any> : Manifest {
  public fun inheritFrom(vararg inheritPaths: Any): InheritManifest<T>

  public fun inheritFrom(vararg inheritPaths: Any, action: Action<T>?): InheritManifest<T>
}
