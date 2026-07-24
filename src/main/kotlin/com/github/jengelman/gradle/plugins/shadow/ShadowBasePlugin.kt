package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.BuildConfig.DEFAULT_R8_DEPENDENCY
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
        addShadowJarToAssembleLifecycle.convention(true)
      }
      @Suppress("EagerGradleConfiguration") // this should be created eagerly.
      configurations.create(CONFIGURATION_NAME) {
        it.description = "Specify runtime dependencies that are not merged into the final JAR."
      }
      configurations.register(R8_CONFIGURATION_NAME) {
        it.description = "R8 executable used by ShadowJar post-processing."
        it.isCanBeConsumed = false
        // Defer the dependency resolving.
        it.isCanBeResolved = false
        it.defaultDependencies { dependencies ->
          dependencies.add(project.dependencies.create(DEFAULT_R8_DEPENDENCY))
        }
      }
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
    public const val R8_CONFIGURATION_NAME: String = "shadowR8"

    @get:JvmSynthetic
    public inline val ConfigurationContainer.shadow: NamedDomainObjectProvider<Configuration>
      get() = named(CONFIGURATION_NAME)

    @get:JvmSynthetic
    public inline val Project.shadow: ShadowExtension
      get() = extensions.getByType(ShadowExtension::class.java)
  }
}
