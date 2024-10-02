package com.github.jengelman.gradle.plugins.shadow.legacy

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Empty plugin to still have the [com.github.johnrengelman.shadow](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow) plugin applied.
 *
 * This allows older build logic to keep on working as if that old plugin ID was applied.
 */
public class LegacyShadowPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = Unit
}
