package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.provider.Property

public interface ShadowExtension {
  /**
   * Controls whether the optional Java variant is added to the 'java' component.
   * If true, the variant from the shadow runtime elements configuration will be mapped as optional.
   * This affects how consumers resolve the published artifact.
   */
  public val addOptionalJavaVariant: Property<Boolean>
}
