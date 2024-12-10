package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import org.jetbrains.annotations.NotNull

class MavenFileModule extends AbstractMavenModule {
    MavenFileModule(File moduleDir, String groupId, String artifactId, String version) {
        super(moduleDir, groupId, artifactId, version)
    }

    @Override
    boolean isUniqueSnapshots() {
        return true
    }

    @Override
    String getMetaDataFileContent() {
        """
            <metadata>
              <!-- ${getArtifactContent()} -->
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
        """.stripIndent()
    }

    @Override
    protected void onPublish(@NotNull File file) {
        sha1File(file)
        md5File(file)
    }

    @Override
    protected boolean isPublishesMetaDataFile() {
        uniqueSnapshots && version.endsWith("-SNAPSHOT")
    }
}
