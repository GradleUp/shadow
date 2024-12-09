package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import java.io.File
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NotNull

class MavenFileModule(
  moduleDir: File,
  groupId: String,
  artifactId: String,
  version: String,
) : AbstractMavenModule(moduleDir, groupId, artifactId, version) {

  override fun getUniqueSnapshots(): Boolean = uniqueSnapshots

  @Language("XML")
  override fun getMetaDataFileContent(): String {
    return """
      <metadata>
        <!-- $artifactContent -->
        <groupId>$groupId</groupId>
        <artifactId>$artifactId</artifactId>
        <version>$version</version>
        <versioning>
          <snapshot>
            <timestamp>${timestampFormat.format(publishTimestamp)}</timestamp>
            <buildNumber>$publishCount</buildNumber>
          </snapshot>
          <lastUpdated>${updateFormat.format(publishTimestamp)}</lastUpdated>
        </versioning>
      </metadata>
    """.trimIndent()
  }

  override fun onPublish(@NotNull file: File) {
    sha1File(file)
    md5File(file)
  }

  override fun publishesMetaDataFile(): Boolean {
    return uniqueSnapshots && version.endsWith("-SNAPSHOT")
  }
}
