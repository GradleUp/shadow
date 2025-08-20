package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.provider.Property

public interface ShadowExtension {
  /**
   * If `true`, publishes the [ShadowJavaPlugin.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME] as an optional variant of
   * the `java` component. This affects how consumers resolve the published artifact.
   *
   * Defaults to `true`.
   */
  public val addShadowVariantIntoJavaComponent: Property<Boolean>
}
