package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import com.github.jengelman.gradle.plugins.shadow.util.file.TestFile
import com.github.jengelman.gradle.plugins.shadow.util.repo.AbstractModule
import groovy.xml.MarkupBuilder
import groovy.xml.XmlParser
import java.text.SimpleDateFormat

abstract class AbstractMavenModule extends AbstractModule implements MavenModule {
    protected static final String MAVEN_METADATA_FILE = "maven-metadata.xml"
    final TestFile moduleDir
    final String groupId
    final String artifactId
    final String version
    String parentPomSection
    String type = 'jar'
    String packaging
    int publishCount = 1
    private final List dependencies = []
    private final List artifacts = []
    final updateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    final timestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss")

    AbstractMavenModule(TestFile moduleDir, String groupId, String artifactId, String version) {
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    MavenModule parent(String group, String artifactId, String version) {
        parentPomSection = """
<parent>
  <groupId>${group}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${version}</version>
</parent>
"""
        return this
    }

    TestFile getArtifactFile(Map options = [:]) {
        if (version.endsWith("-SNAPSHOT") && !metaDataFile.exists() && uniqueSnapshots) {
            def artifact = toArtifact(options)
            return moduleDir.file("${artifactId}-${version}${artifact.classifier ? "-${artifact.classifier}" : ""}.${artifact.type}")
        }
        return artifactFile(options)
    }

    abstract boolean getUniqueSnapshots()

    String getPublishArtifactVersion() {
        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            return "${version.replaceFirst('-SNAPSHOT$', '')}-${getUniqueSnapshotVersion()}"
        }
        return version
    }

    private String getUniqueSnapshotVersion() {
        assert uniqueSnapshots && version.endsWith('-SNAPSHOT')
        if (metaDataFile.isFile()) {
            def metaData = new XmlParser().parse(metaDataFile.assertIsFile())
            def timestamp = metaData.versioning.snapshot.timestamp[0].text().trim()
            def build = metaData.versioning.snapshot.buildNumber[0].text().trim()
            return "${timestamp}-${build}"
        }
        return "${timestampFormat.format(publishTimestamp)}-${publishCount}"
    }

    MavenModule dependsOn(String... dependencyArtifactIds) {
        for (String id : dependencyArtifactIds) {
            dependsOn(groupId, id, '1.0')
        }
        return this
    }

    MavenModule dependsOn(String group, String artifactId, String version, String type = null) {
        this.dependencies << [groupId: group, artifactId: artifactId, version: version, type: type]
        return this
    }

    MavenModule hasPackaging(String packaging) {
        this.packaging = packaging
        return this
    }

    /**
     * Specifies the type of the main artifact.
     */
    MavenModule hasType(String type) {
        this.type = type
        return this
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    MavenModule artifact(Map<String, ?> options) {
        artifacts << options
        return this
    }

    String getPackaging() {
        return packaging
    }

    List getDependencies() {
        return dependencies
    }

    List getArtifacts() {
        return artifacts
    }

    void assertNotPublished() {
        pomFile.assertDoesNotExist()
    }

    void assertPublished() {
        assert pomFile.assertExists()
        assert parsedPom.groupId == groupId
        assert parsedPom.artifactId == artifactId
        assert parsedPom.version == version
    }

    void assertPublishedAsPomModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == "pom"
    }

    void assertPublishedAsJavaModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.jar", "${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == null
    }

    void assertPublishedAsWebModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.war", "${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == 'war'
    }

    void assertPublishedAsEarModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.ear", "${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == 'ear'
    }

    /**
     * Asserts that exactly the given artifacts have been deployed, along with their checksum files
     */
    void assertArtifactsPublished(String... names) {
        def artifactNames = names as Set
        if (publishesMetaDataFile()) {
            artifactNames.add(MAVEN_METADATA_FILE)
        }
        assert moduleDir.isDirectory()
        Set actual = moduleDir.list() as Set
        for (name in artifactNames) {
            assert actual.remove(name)

            if(publishesHashFiles()) {
                assert actual.remove("${name}.md5" as String)
                assert actual.remove("${name}.sha1" as String)
            }
        }
        assert actual.isEmpty()
    }

    //abstract String getPublishArtifactVersion()

    MavenPom getParsedPom() {
        return new MavenPom(pomFile)
    }

    DefaultMavenMetaData getRootMetaData() {
        new DefaultMavenMetaData(rootMetaDataFile)
    }

    TestFile getPomFile() {
        return moduleDir.file("$artifactId-${publishArtifactVersion}.pom")
    }

    TestFile getMetaDataFile() {
        moduleDir.file(MAVEN_METADATA_FILE)
    }

    TestFile getRootMetaDataFile() {
        moduleDir.parentFile.file(MAVEN_METADATA_FILE)
    }

    TestFile artifactFile(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def fileName = "$artifactId-${publishArtifactVersion}.${artifact.type}"
        if (artifact.classifier) {
            fileName = "$artifactId-$publishArtifactVersion-${artifact.classifier}.${artifact.type}"
        }
        return moduleDir.file(fileName)
    }

    MavenModule publishWithChangedContent() {
        publishCount++
        return publish()
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

    MavenModule publishPom() {
        moduleDir.createDir()
        def rootMavenMetaData = getRootMetaDataFile()

        updateRootMavenMetaData(rootMavenMetaData)

        if (publishesMetaDataFile()) {
            publish(metaDataFile) { Writer writer ->
                writer << getMetaDataFileContent()
            }
        }

        publish(pomFile) { Writer writer ->
            def pomPackaging = packaging ?: type
            writer << """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <!-- ${getArtifactContent()} -->
  <modelVersion>4.0.0</modelVersion>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <packaging>$pomPackaging</packaging>
  <version>$version</version>
  <description>Published on $publishTimestamp</description>"""

            if (parentPomSection) {
                writer << "\n$parentPomSection\n"
            }

            if (!dependencies.empty) {
                writer << """
  <dependencies>"""
            }

            dependencies.each { dependency ->
                def typeAttribute = dependency['type'] == null ? "" : "<type>$dependency.type</type>"
                writer << """
    <dependency>
      <groupId>$dependency.groupId</groupId>
      <artifactId>$dependency.artifactId</artifactId>
      <version>$dependency.version</version>
      $typeAttribute
    </dependency>"""
            }

            if (!dependencies.empty) {
                writer << """
  </dependencies>"""
            }

            writer << "\n</project>"
        }
        return this
    }

    private void updateRootMavenMetaData(TestFile rootMavenMetaData) {
        def allVersions = rootMavenMetaData.exists() ? new XmlParser().parseText(rootMavenMetaData.text).versioning.versions.version*.value().flatten() : []
        allVersions << version
        publish(rootMavenMetaData) { Writer writer ->
            def builder = new MarkupBuilder(writer)
            builder.metadata {
                groupId(groupId)
                artifactId(artifactId)
                version(allVersions.max())
                versioning {
                    if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
                        snapshot {
                            timestamp(timestampFormat.format(publishTimestamp))
                            buildNumber(publishCount)
                            lastUpdated(updateFormat.format(publishTimestamp))
                        }
                    } else {
                        versions {
                            allVersions.each {currVersion ->
                                version(currVersion)
                            }
                        }
                    }
                }
            }
        }
    }

    abstract String getMetaDataFileContent()

    MavenModule publish() {

        publishPom()
        artifacts.each { artifact ->
            publishArtifact(artifact)
        }
        publishArtifact([:])
        return this
    }

    File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        if (type == 'pom') {
            return artifactFile
        }
        publish(artifactFile) { Writer writer ->
            writer << "${artifactFile.name} : $artifactContent"
        }
        return artifactFile
    }

    protected String getArtifactContent() {
        // Some content to include in each artifact, so that its size and content varies on each publish
        return (0..publishCount).join("-")
    }

    protected abstract boolean publishesMetaDataFile()
    protected abstract boolean publishesHashFiles()
}
