package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

@Deprecated("This is deprecated since 8.3.2")
public abstract class ShadowExtension(project: Project) {
  private val components = project.components

  @Deprecated("configure publication using component.shadow directly.")
  public fun component(publication: MavenPublication) {
    publication.from(components.findByName(ShadowBasePlugin.COMPONENT_NAME))
  }
}
