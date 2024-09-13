package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentContainer
import org.gradle.api.publish.maven.MavenPublication

class ShadowExtension {
    private final SoftwareComponentContainer components

    ShadowExtension(Project project) {
        components = project.components
    }

    /**
     * @param publication
     * @deprecated configure publication using component.shadow directly
     */
    @Deprecated
    void component(MavenPublication publication) {
        publication.from(components.findByName("shadow"))
    }
}
