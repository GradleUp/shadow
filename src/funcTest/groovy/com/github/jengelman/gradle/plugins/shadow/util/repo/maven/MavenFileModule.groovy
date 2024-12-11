package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import org.apache.maven.artifact.repository.metadata.Metadata
import org.apache.maven.artifact.repository.metadata.Snapshot
import org.apache.maven.artifact.repository.metadata.Versioning
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
    Metadata getMetaData(List<String> versions) {
        Metadata metadata = new Metadata()
        metadata.groupId = groupId
        metadata.artifactId = artifactId
        metadata.version = version

        Versioning versioning = new Versioning()
        versioning.versions = versions
        versioning.snapshot = new Snapshot()
        versioning.snapshot.timestamp = timestampFormat.format(publishTimestamp)
        versioning.snapshot.buildNumber = publishCount
        versioning.lastUpdated = updateFormat.format(publishTimestamp)
        metadata.versioning = versioning

        return metadata
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
