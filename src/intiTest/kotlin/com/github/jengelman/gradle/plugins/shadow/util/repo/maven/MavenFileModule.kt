package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import java.nio.file.Path
import org.apache.maven.artifact.repository.metadata.Metadata
import org.apache.maven.artifact.repository.metadata.Snapshot
import org.apache.maven.artifact.repository.metadata.Versioning

open class MavenFileModule(
  moduleDir: Path,
  groupId: String,
  artifactId: String,
  version: String,
) : AbstractMavenModule(moduleDir, groupId, artifactId, version) {

  override val isUniqueSnapshots: Boolean = true

  override fun getMetaData(versions: List<String>): Metadata {
    return Metadata().also {
      it.groupId = groupId
      it.artifactId = artifactId
      it.version = version
      it.versioning = Versioning().also { versioning ->
        versioning.versions = versions
        versioning.snapshot = Snapshot().apply {
          timestamp = timestampFormat.format(publishTimestamp)
          buildNumber = publishCount
        }
        versioning.lastUpdated = updateFormat.format(publishTimestamp)
      }
    }
  }

  override val isPublishesMetaDataFile: Boolean
    get() = isUniqueSnapshots && version.endsWith("-SNAPSHOT")
}
