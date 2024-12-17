package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.util.repo.maven.MavenFileModule
import java.io.File

class AppendableMavenFileModule(
  moduleDir: File,
  groupId: String,
  artifactId: String,
  version: String,
) : MavenFileModule(moduleDir, groupId, artifactId, version) {

  private val contents = mutableMapOf<String, MutableMap<String, String>>().withDefault { mutableMapOf() }
  private val files = mutableMapOf<String, File>()

  fun use(file: File): AppendableMavenFileModule {
    return use("", file)
  }

  fun use(classifier: String, file: File): AppendableMavenFileModule = apply {
    files[classifier] = file
  }

  fun insertFile(path: String, content: String): AppendableMavenFileModule {
    return insertFile("", path, content)
  }

  fun insertFile(classifier: String, path: String, content: String): AppendableMavenFileModule = apply {
    contents.getOrPut(classifier) { mutableMapOf() }[path] = content
  }

  override fun publishArtifact(artifact: Map<String, Any?>): File {
    val artifactFile = artifactFile(artifact)
    if (type == "pom") {
      return artifactFile
    }
    val classifier = artifact["classifier"] as? String ?: ""
    val classifierFile = files[classifier]
    if (classifierFile != null) {
      publish(artifactFile) { os ->
        classifierFile.inputStream().copyTo(os)
      }
    } else {
      publish(artifactFile) { os ->
        AppendableJar(contents[classifier].orEmpty()).write(os)
      }
    }
    return artifactFile
  }
}
