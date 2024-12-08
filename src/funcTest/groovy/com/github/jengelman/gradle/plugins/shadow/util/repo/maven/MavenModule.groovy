package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

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

    MavenModule dependsOn(String group, String artifactId, String version)

    File getPomFile()

    File getMetaDataFile()
}
