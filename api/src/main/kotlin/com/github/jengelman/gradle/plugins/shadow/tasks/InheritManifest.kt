package com.github.jengelman.gradle.plugins.shadow.tasks

import groovy.lang.Closure
import org.gradle.api.java.archives.Manifest

public interface InheritManifest : Manifest {
  public fun inheritFrom(vararg inheritPaths: Any): InheritManifest

  public fun inheritFrom(vararg inheritPaths: Any, closure: Closure<*>?): InheritManifest
}
