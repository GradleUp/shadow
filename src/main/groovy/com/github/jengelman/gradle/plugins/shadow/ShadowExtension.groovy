package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.Project
import org.gradle.api.file.CopySpec

class ShadowExtension {

    CopySpec applicationDistribution

    ShadowExtension(Project project) {
        applicationDistribution = project.copySpec {}
    }
}
