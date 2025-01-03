package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.util.repo.maven.MavenFileRepository
import java.nio.file.Path

class AppendableMavenFileRepository(rootDir: Path) : MavenFileRepository(rootDir) {

  override fun module(groupId: String, artifactId: String, version: String): AppendableMavenFileModule {
    return AppendableMavenFileModule(super.module(groupId, artifactId, version))
  }
}
