package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import java.net.URI
import java.nio.file.Path

/**
 * A fixture for dealing with file Maven repositories.
 */
open class MavenFileRepository(val rootDir: Path) : MavenRepository {

  override val uri: URI = rootDir.toUri()

  override fun module(groupId: String, artifactId: String): MavenFileModule {
    return module(groupId = groupId, artifactId = artifactId, version = "0.0.0")
  }

  override fun module(groupId: String, artifactId: String, version: String): MavenFileModule {
    val artifactDir = rootDir.resolve("${groupId.replace('.', '/')}/$artifactId/$version")
    return MavenFileModule(artifactDir, groupId, artifactId, version)
  }
}
