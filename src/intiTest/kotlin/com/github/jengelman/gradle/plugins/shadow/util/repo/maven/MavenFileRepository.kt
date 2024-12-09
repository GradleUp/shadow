package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import java.io.File
import java.net.URI

/**
 * A fixture for dealing with file Maven repositories.
 */
abstract class MavenFileRepository(private val rootDir: File) : MavenRepository {

  override val uri: URI = rootDir.toURI()

  override fun module(groupId: String, artifactId: String): MavenModule {
    return module(groupId, artifactId, "1.0")
  }

  override fun module(groupId: String, artifactId: String, version: String): MavenModule {
    val artifactDir = rootDir.resolve("${groupId.replace('.', '/')}/$artifactId/$version")
    return MavenFileModule(artifactDir, groupId, artifactId, version)
  }
}
