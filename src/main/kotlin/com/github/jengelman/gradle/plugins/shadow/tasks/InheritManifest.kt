package com.github.jengelman.gradle.plugins.shadow.tasks

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.java.archives.Manifest

interface InheritManifest : Manifest {
  fun inheritFrom(vararg inheritPaths: Any): InheritManifest

  fun inheritFrom(vararg inheritPaths: Any, action: Action<InheritManifest>?): InheritManifest
}
