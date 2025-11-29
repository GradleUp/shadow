package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.util.GradleVersion

/**
 * Return `runtimeClasspath` or `runtime` configuration.
 */
internal inline val Project.runtimeConfiguration: Configuration
  get() = configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
    ?: configurations.getByName("runtime")

internal inline val Project.sourceSets: SourceSetContainer
  get() = extensions.getByType(SourceSetContainer::class.java)

internal inline val Project.distributions: DistributionContainer
  get() = extensions.getByType(DistributionContainer::class.java)

internal inline val Project.applicationExtension: JavaApplication
  get() = extensions.getByType(JavaApplication::class.java)

internal inline val Project.javaPluginExtension: JavaPluginExtension
  get() = extensions.getByType(JavaPluginExtension::class.java)

internal inline val Project.javaToolchainService: JavaToolchainService
  get() = extensions.getByType(JavaToolchainService::class.java)

// ExtraPropertiesExtension is IP safe and contains properties from both the root `gradle.properties` and the
// subproject's `gradle.properties`. See https://github.com/gradle/gradle/issues/29600#issuecomment-3580868326.
internal fun Project.findOptionalProperty(propertyName: String): String? {
  val extras = checkNotNull(extensions.findByType(ExtraPropertiesExtension::class.java))
  return if (extras.has(propertyName)) extras.get(propertyName)?.toString() else null
}

internal fun Project.addBuildScanCustomValues() {
  val develocity = extensions.findByType(DevelocityConfiguration::class.java) ?: return
  val buildScan = develocity.buildScan
  tasks.withType(ShadowJar::class.java).configureEach { task ->
    buildScan.buildFinished {
      buildScan.value("shadow.${task.path}.executed", "true")
      buildScan.value("shadow.${task.path}.didWork", task.didWork.toString())
    }
  }
}

/**
 * TODO: this could be removed after bumping the min Gradle requirement to 9.2 or above.
 */
@Suppress("UnstableApiUsage")
internal fun AdhocComponentWithVariants.addVariantsFromConfigurationCompat(
  outgoingConfiguration: NamedDomainObjectProvider<Configuration>,
  action: Action<in ConfigurationVariantDetails>,
) {
  if (GradleVersion.current() >= GradleVersion.version("9.2")) {
    @Suppress("UNCHECKED_CAST")
    addVariantsFromConfiguration(outgoingConfiguration as Provider<ConsumableConfiguration>, action)
  } else {
    addVariantsFromConfiguration(outgoingConfiguration.get(), action)
  }
}

internal inline fun <reified V : Any> ObjectFactory.property(
  defaultValue: Any? = null,
): Property<V> = property(V::class.java).apply {
  defaultValue ?: return@apply
  if (defaultValue is Provider<*>) {
    @Suppress("UNCHECKED_CAST")
    convention(defaultValue as Provider<V>)
  } else {
    convention(defaultValue as V)
  }
}

@Suppress("UNCHECKED_CAST")
internal inline fun <reified V : Any> ObjectFactory.setProperty(
  defaultValue: Any? = null,
): SetProperty<V> = setProperty(V::class.java).apply {
  defaultValue ?: return@apply
  if (defaultValue is Provider<*>) {
    convention(defaultValue as Provider<Iterable<V>>)
  } else {
    convention(defaultValue as Iterable<V>)
  }
}

@Suppress("UNCHECKED_CAST")
internal inline fun <reified V : Any> ObjectFactory.mapProperty(
  defaultValue: Any? = null,
): MapProperty<String, V> = mapProperty(String::class.java, V::class.java).apply {
  defaultValue ?: return@apply
  if (defaultValue is Provider<*>) {
    convention(defaultValue as Provider<Map<String, V>>)
  } else {
    convention(defaultValue as Map<String, V>)
  }
}

internal inline fun ObjectFactory.fileCollection(
  path: () -> Any,
): ConfigurableFileCollection = fileCollection().apply {
  @Suppress("UnstableApiUsage")
  convention(path())
}
