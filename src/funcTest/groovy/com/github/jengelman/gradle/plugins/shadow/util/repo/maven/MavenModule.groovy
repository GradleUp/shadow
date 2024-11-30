package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import com.github.jengelman.gradle.plugins.shadow.util.file.TestFile

interface MavenModule {
    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module. Publishes only those artifacts whose content has
     * changed since the last call to {@code #publish()}.
     */
    MavenModule publish()

    /**
     * Publishes the pom.xml only
     */
    MavenModule publishPom()

    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module, with different content (and size) to any
     * previous publication.
     */
    MavenModule publishWithChangedContent()

    MavenModule withNonUniqueSnapshots()

    MavenModule parent(String group, String artifactId, String version)

    MavenModule dependsOn(String group, String artifactId, String version)

    MavenModule hasPackaging(String packaging)

    /**
     * Sets the type of the main artifact for this module.
     */
    MavenModule hasType(String type)

    TestFile getPomFile()

    TestFile getArtifactFile()

    TestFile getMetaDataFile()

    MavenPom getParsedPom()

    MavenMetaData getRootMetaData()
}
