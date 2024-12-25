package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.util.repo.maven.MavenFileModule
import java.nio.file.Path
import kotlin.io.path.inputStream

class AppendableMavenFileModule(module: MavenFileModule) : MavenFileModule(module.moduleDir, module.groupId, module.artifactId, module.version) {

  private val contents = mutableMapOf<String, MutableMap<String, String>>().withDefault { mutableMapOf() }
  private val paths = mutableMapOf<String, Path>()

  fun use(path: Path): AppendableMavenFileModule {
    return use("", path)
  }

  fun use(classifier: String, path: Path): AppendableMavenFileModule = apply {
    paths[classifier] = path
  }

  fun insertFile(path: String, content: String): AppendableMavenFileModule {
    return insertFile("", path, content)
  }

  fun insertFile(classifier: String, path: String, content: String): AppendableMavenFileModule = apply {
    contents.getOrPut(classifier) { mutableMapOf() }[path] = content
  }

  override fun publishArtifact(artifact: Map<String, Any?>): Path {
    val artifactPath = artifactPath(artifact)
    if (type == "pom") {
      return artifactPath
    }
    val classifier = artifact["classifier"] as? String ?: ""
    val classifierPath = paths[classifier]
    if (classifierPath != null) {
      publish(artifactPath) { os ->
        classifierPath.inputStream().copyTo(os)
      }
    } else {
      publish(artifactPath) { os ->
        AppendableJar.write(contents[classifier].orEmpty(), os)
      }
    }
    return artifactPath
  }
}
