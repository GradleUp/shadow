package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import java.io.File

interface MavenModule {
  /**
   * Publishes the `pom.xml` plus main artifact, plus any additional artifacts for this module.
   * Publishes only those artifacts whose content has changed since the last call to `publish()`.
   */
  fun publish(): MavenModule

  /**
   * Publishes the `pom.xml` only
   */
  fun publishPom(): MavenModule

  fun dependsOn(group: String, artifactId: String, version: String): MavenModule

  val pomFile: File

  val metaDataFile: File
}
