package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.provider.Property

public interface ShadowExtension {
  /**
   * If `true`, publishes the [ShadowJavaPlugin.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME] as an optional variant of
   * the `java` component. This affects how consumers resolve the published artifact.
   *
   * Defaults to `true`.
   */
  public val addShadowVariantIntoJavaComponent: Property<Boolean>

  /**
   * If `true`, adds a [TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE] attribute to the Gradle Module Metadata of the
   * shadowed JAR. This attribute indicates the target JVM version for which the shadow JAR is built.
   *
   * Defaults to `true`.
   */
  public val addTargetJvmVersionAttribute: Property<Boolean>
}
