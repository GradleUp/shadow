package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import com.github.jengelman.gradle.plugins.shadow.util.repo.AbstractModule
import org.apache.maven.artifact.repository.metadata.Metadata
import org.apache.maven.artifact.repository.metadata.Snapshot
import org.apache.maven.artifact.repository.metadata.Versioning
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer

import java.text.SimpleDateFormat

abstract class AbstractMavenModule extends AbstractModule implements MavenModule {
    protected static final String MAVEN_METADATA_FILE = "maven-metadata.xml"

    protected final File moduleDir
    protected final String groupId
    protected final String artifactId
    protected final String version
    protected final def updateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    protected final def timestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss")
    protected final List<Dependency> dependencies = []
    protected final List<Map<String, String>> artifacts = []

    protected String type = 'jar'
    protected String packaging
    protected int publishCount = 1

    AbstractMavenModule(File moduleDir, String groupId, String artifactId, String version) {
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    protected abstract boolean isUniqueSnapshots()

    protected abstract boolean isPublishesMetaDataFile()

    String getPublishArtifactVersion() {
        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            return "${version.replaceFirst('-SNAPSHOT$', '')}-${getUniqueSnapshotVersion()}"
        }
        return version
    }

    private String getUniqueSnapshotVersion() {
        assert uniqueSnapshots && version.endsWith('-SNAPSHOT')
        if (metaDataFile.isFile()) {
            Metadata metaData = new MetadataXpp3Reader().read(metaDataFile.newReader())
            String timestamp = metaData.versioning.snapshot.timestamp
            String build = metaData.versioning.snapshot.buildNumber
            return "${timestamp}-${build}"
        }
        return "${timestampFormat.format(publishTimestamp)}-${publishCount}"
    }

    MavenModule dependsOn(String artifactId) {
        return dependsOn(groupId, artifactId, '1.0')
    }

    @Override
    MavenModule dependsOn(String groupId, String artifactId, String version) {
        def dep = new Dependency()
        dep.groupId = groupId
        dep.artifactId = artifactId
        dep.version = version
        dependencies.add(dep)
        return this
    }

    @Override
    File getPomFile() {
        return moduleDir.resolve("$artifactId-${publishArtifactVersion}.pom")
    }

    @Override
    File getMetaDataFile() {
        return moduleDir.resolve(MAVEN_METADATA_FILE)
    }

    File getRootMetaDataFile() {
        return moduleDir.parentFile.resolve(MAVEN_METADATA_FILE)
    }

    File artifactFile(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def fileName = "$artifactId-${publishArtifactVersion}.${artifact.type}"
        if (artifact.classifier) {
            fileName = "$artifactId-$publishArtifactVersion-${artifact.classifier}.${artifact.type}"
        }
        return moduleDir.resolve(fileName)
    }

    protected Map<String, Object> toArtifact(Map<String, ?> options) {
        options = new HashMap<String, Object>(options)
        def artifact = [type: options.remove('type') ?: type, classifier: options.remove('classifier') ?: null]
        assert options.isEmpty(): "Unknown options : ${options.keySet()}"
        return artifact
    }

    Date getPublishTimestamp() {
        return new Date(updateFormat.parse("20100101120000").time + publishCount * 1000)
    }

    @Override
    MavenModule publishPom() {
        moduleDir.createDir()
        def rootMavenMetaData = getRootMetaDataFile()
        updateRootMavenMetaData(rootMavenMetaData)

        if (publishesMetaDataFile) {
            publish(metaDataFile) { OutputStream outputStream ->
                MetadataXpp3Writer metadataWriter = new MetadataXpp3Writer()
                metadataWriter.write(outputStream, getMetaData([]))
            }
        }

        publish(pomFile) { OutputStream outputStream ->
            def pomPackaging = packaging ?: type
            // Create a new Maven Model
            Model model = new Model()
            model.modelVersion = '4.0.0'
            model.groupId = groupId
            model.artifactId = artifactId
            model.version = version
            model.packaging = pomPackaging
            model.description = "Published on $publishTimestamp"
            model.dependencies = dependencies

            // Write the model to the POM file
            MavenXpp3Writer pomWriter = new MavenXpp3Writer()
            pomWriter.write(outputStream, model)
        }
        return this
    }

    private void updateRootMavenMetaData(File rootMavenMetaData) {
        List<String> allVersions = []
        if (rootMavenMetaData.exists()) {
            def metaData = new MetadataXpp3Reader().read(rootMavenMetaData.newReader())
            allVersions = metaData.versioning.versions
        }
        allVersions << version
        publish(rootMavenMetaData) { OutputStream outputStream ->
            MetadataXpp3Writer metadataWriter = new MetadataXpp3Writer()
            metadataWriter.write(outputStream, getMetaData(allVersions))
        }
    }

    /**
     * Similar to updateRootMavenMetaData but for the artifact's own metadata file
     */
    Metadata getMetaData(List<String> versions) {
        Metadata metadata = new Metadata()
        metadata.groupId = groupId
        metadata.artifactId = artifactId
        metadata.version = version

        Versioning versioning = new Versioning()
        versioning.versions = versions
        versioning.lastUpdated = updateFormat.format(publishTimestamp)
        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            Snapshot snapshot = new Snapshot()
            snapshot.timestamp = timestampFormat.format(publishTimestamp)
            snapshot.buildNumber = publishCount
            versioning.snapshot = snapshot
        }
        metadata.versioning = versioning
        return metadata
    }

    @Override
    MavenModule publish() {
        publishPom()
        artifacts.each { artifact ->
            publishArtifact(artifact as Map<String, ?>)
        }
        publishArtifact([:])
        return this
    }

    File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        if (type == 'pom') {
            return artifactFile
        }
        publish(artifactFile) { OutputStream outputStream ->
            outputStream.write("${artifactFile.name} : $artifactContent".bytes)
        }
        return artifactFile
    }

    protected String getArtifactContent() {
        // Some content to include in each artifact, so that its size and content varies on each publish
        return (0..publishCount).join("-")
    }
}
