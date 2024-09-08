package com.github.jengelman.gradle.plugins.shadow.legacy

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Empty plugin to still have the com.github.johnrengelman.shadow plugin applied.
 *
 * This allows older build logic to keep on working as if that old plugin ID was applied.
 */
class LegacyShadowPlugin implements Plugin<Project> {
    @Override
    void apply(Project target) {
        // Do nothing
    }
}
