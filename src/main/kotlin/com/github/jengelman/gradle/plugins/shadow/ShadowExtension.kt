package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.attributes.Bundling
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
   * shadowed JAR. This affects how consumers resolve the published artifact based on the target JVM version.
   *
   * Defaults to `true`.
   */
  public val addTargetJvmVersionAttribute: Property<Boolean>

  /**
   * The [Bundling] attribute to use for the Gradle Module Metadata.
   *
   * Per description of the attribute, you should set it to either [Bundling.SHADOWED] or [Bundling.EMBEDDED].
   *
   * Defaults to [Bundling.SHADOWED].
   */
  public val bundlingAttribute: Property<String>
}
