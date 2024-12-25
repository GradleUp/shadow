package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import java.net.URI

/**
 * A fixture for dealing with Maven repositories.
 */
interface MavenRepository {
  val uri: URI

  fun module(groupId: String, artifactId: String, version: String): MavenModule
}
