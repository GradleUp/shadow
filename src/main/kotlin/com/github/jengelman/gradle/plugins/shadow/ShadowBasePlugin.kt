package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.CONFIGURATION_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.EXTENSION_NAME
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.Bundling
import org.gradle.api.component.SoftwareComponentContainer
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.plugins.ExtensionContainer

public abstract class ShadowBasePlugin : Plugin<Project> {

  override fun apply(project: Project): Unit =
    with(project) {
      with(extensions.create(EXTENSION_NAME, ShadowExtension::class.java)) {
        addShadowVariantIntoJavaComponent.convention(true)
        addTargetJvmVersionAttribute.convention(true)
        bundlingAttribute.convention(Bundling.SHADOWED)
      }
      @Suppress("EagerGradleConfiguration") // this should be created eagerly.
      configurations.create(CONFIGURATION_NAME)
    }

  public companion object {
    /**
     * Most of the components registered by Shadow plugin will use this name (`shadow`).
     * - [ExtensionContainer.create]
     * - [ConfigurationContainer.register]
     * - [SoftwareComponentContainer.register]
     * - [DistributionContainer.register]
     *
     * and so on.
     *
     * @see [EXTENSION_NAME]
     * @see [CONFIGURATION_NAME]
     * @see [ShadowApplicationPlugin.DISTRIBUTION_NAME]
     * @see [ShadowJavaPlugin.COMPONENT_NAME]
     */
    public const val SHADOW: String = "shadow"
    public const val EXTENSION_NAME: String = SHADOW
    public const val CONFIGURATION_NAME: String = SHADOW

    @get:JvmSynthetic
    public inline val ConfigurationContainer.shadow: NamedDomainObjectProvider<Configuration>
      get() = named(CONFIGURATION_NAME)

    @get:JvmSynthetic
    public inline val Project.shadow: ShadowExtension
      get() = extensions.getByType(ShadowExtension::class.java)
  }
}
