package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

class MavenFileModule extends AbstractMavenModule {
    private boolean uniqueSnapshots = true

    MavenFileModule(File moduleDir, String groupId, String artifactId, String version) {
        super(moduleDir, groupId, artifactId, version)
    }

    @Override
    boolean getUniqueSnapshots() {
        return uniqueSnapshots
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
    protected onPublish(File file) {
        sha1File(file)
        md5File(file)
    }

    @Override
    protected boolean publishesMetaDataFile() {
        uniqueSnapshots && version.endsWith("-SNAPSHOT")
    }
}
