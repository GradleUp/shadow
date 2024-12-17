package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import com.github.jengelman.gradle.plugins.shadow.util.repo.AbstractModule
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import org.apache.maven.artifact.repository.metadata.Metadata
import org.apache.maven.artifact.repository.metadata.Snapshot
import org.apache.maven.artifact.repository.metadata.Versioning
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer

abstract class AbstractMavenModule(
  protected val moduleDir: File,
  protected val groupId: String,
  protected val artifactId: String,
  protected val version: String,
) : AbstractModule(),
  MavenModule {

  protected val updateFormat = SimpleDateFormat("yyyyMMddHHmmss")
  protected val timestampFormat = SimpleDateFormat("yyyyMMdd.HHmmss")
  protected val dependencies = mutableListOf<Dependency>()
  protected val artifacts = mutableListOf<Map<String, String>>()

  protected var type: String = "jar"
  protected var packaging: String? = null
  protected var publishCount: Int = 1

  protected abstract val isUniqueSnapshots: Boolean
  protected abstract val isPublishesMetaDataFile: Boolean

  fun dependsOn(artifactId: String): MavenModule {
    return dependsOn(groupId = groupId, artifactId = artifactId, version = version)
  }

  override fun dependsOn(groupId: String, artifactId: String, version: String): MavenModule = apply {
    val dep = Dependency().also {
      it.groupId = groupId
      it.artifactId = artifactId
      it.version = version
    }
    dependencies.add(dep)
  }

  override val pomFile: File
    get() = moduleDir.resolve("$artifactId-$publishArtifactVersion.pom")

  override val metaDataFile: File
    get() = moduleDir.resolve(MAVEN_METADATA_FILE)

  val rootMetaDataFile: File
    get() = moduleDir.resolveSibling(MAVEN_METADATA_FILE)

  fun artifactFile(options: Map<String, Any?>): File {
    val artifact = toArtifact(options)
    var fileName = "$artifactId-$publishArtifactVersion.${artifact["type"]}"
    if (artifact["classifier"] != null) {
      fileName = "$artifactId-$publishArtifactVersion-${artifact["classifier"]}.${artifact["type"]}"
    }
    return moduleDir.resolve(fileName)
  }

  override fun publishPom(): MavenModule = apply {
    moduleDir.mkdirs()
    val rootMavenMetaData = rootMetaDataFile
    updateRootMavenMetaData(rootMavenMetaData)

    if (isPublishesMetaDataFile) {
      publish(metaDataFile) { outputStream ->
        MetadataXpp3Writer().write(outputStream, getMetaData(emptyList()))
      }
    }

    publish(pomFile) { outputStream ->
      val pomPackaging = packaging ?: type
      val model = Model().also {
        it.modelVersion = "4.0.0"
        it.groupId = groupId
        it.artifactId = artifactId
        it.version = version
        it.packaging = pomPackaging
        it.description = "Published on $publishTimestamp"
        it.dependencies = dependencies
      }
      MavenXpp3Writer().write(outputStream, model)
    }
  }

  open fun getMetaData(versions: List<String>): Metadata = Metadata().also {
    it.groupId = groupId
    it.artifactId = artifactId
    it.version = version
    it.versioning = Versioning().also { versioning ->
      versioning.versions = versions
      versioning.lastUpdated = updateFormat.format(publishTimestamp)
      if (isUniqueSnapshots && version.endsWith("-SNAPSHOT")) {
        versioning.snapshot = Snapshot().apply {
          timestamp = timestampFormat.format(publishTimestamp)
          buildNumber = publishCount
        }
      }
    }
  }

  override fun publish(): MavenModule = apply {
    publishPom()
    artifacts.forEach { artifact ->
      publishArtifact(artifact)
    }
    publishArtifact(emptyMap())
  }

  open fun publishArtifact(artifact: Map<String, Any?>): File {
    val artifactFile = artifactFile(artifact)
    if (type == "pom") {
      return artifactFile
    }
    publish(artifactFile) { outputStream ->
      outputStream.write("${artifactFile.name} : $artifactContent".toByteArray())
    }
    return artifactFile
  }

  protected fun toArtifact(options: Map<String, Any?>): Map<String, Any?> {
    val artifact = mutableMapOf(
      "type" to (options["type"] ?: type),
      "classifier" to options["classifier"],
    )
    require(options.keys.isEmpty()) { "Unknown options : ${options.keys}" }
    return artifact
  }

  protected val publishArtifactVersion: String
    get() = if (isUniqueSnapshots && version.endsWith("-SNAPSHOT")) {
      "${version.removeSuffix("-SNAPSHOT")}-$uniqueSnapshotVersion"
    } else {
      version
    }

  protected val publishTimestamp: Date
    get() = Date(updateFormat.parse("20100101120000").time + publishCount * 1000)

  private fun updateRootMavenMetaData(rootMavenMetaData: File) {
    val allVersions = if (rootMavenMetaData.exists()) {
      MetadataXpp3Reader().read(rootMavenMetaData.reader()).versioning.versions
    } else {
      mutableListOf()
    }
    allVersions.add(version)
    publish(rootMavenMetaData) { outputStream ->
      MetadataXpp3Writer().write(outputStream, getMetaData(allVersions))
    }
  }

  private val artifactContent: String
    // Some content to include in each artifact, so that its size and content varies on each pu
    get() = (0..publishCount).joinToString("-")

  private val uniqueSnapshotVersion: String
    get() {
      require(isUniqueSnapshots && version.endsWith("-SNAPSHOT"))
      return if (metaDataFile.isFile) {
        val metaData = MetadataXpp3Reader().read(metaDataFile.reader())
        val timestamp = metaData.versioning.snapshot.timestamp
        val build = metaData.versioning.snapshot.buildNumber
        "$timestamp-$build"
      } else {
        "${timestampFormat.format(publishTimestamp)}-$publishCount"
      }
    }

  protected companion object {
    const val MAVEN_METADATA_FILE = "maven-metadata.xml"
  }
}
