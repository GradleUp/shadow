package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.SHADOW
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.internal.extendsFromCompat
import com.github.jengelman.gradle.plugins.shadow.internal.javaPluginExtension
import com.github.jengelman.gradle.plugins.shadow.internal.moveGradleApiIntoCompileOnly
import com.github.jengelman.gradle.plugins.shadow.internal.runtimeConfiguration
import com.github.jengelman.gradle.plugins.shadow.internal.sourceSets
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.registerShadowJarCommon
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import javax.inject.Inject
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar

public abstract class ShadowJavaPlugin
@Inject
constructor(private val softwareComponentFactory: SoftwareComponentFactory) : Plugin<Project> {

  override fun apply(project: Project): Unit =
    with(project) {
      configureShadowJar()
      configureConfigurations()
      configureComponents()
      configureJavaGradlePlugin()
    }

  protected open fun Project.configureShadowJar() {
    val taskProvider =
      registerShadowJarCommon(tasks.named("jar", Jar::class.java)) { task ->
        task.from(sourceSets.named("main").map { it.output })
        task.configurations.convention(provider { listOf(runtimeConfiguration) })
      }
    artifacts.add(configurations.shadow.name, taskProvider)
  }

  protected open fun Project.configureConfigurations() {
    val shadowConfig = configurations.shadow

    shadowConfig.configure { configuration ->
      configuration.dependencies.addAllLater(
        shadow.addExcludedDependenciesToShadowConfiguration.flatMap { enabled ->
          if (!enabled) return@flatMap provider { emptyList() }

          tasks.shadowJar.map { shadowJar ->
            val includedFiles = shadowJar.includedDependencies.files
            shadowJar.configurations
              .get()
              .flatMap { it.resolvedConfiguration.resolvedArtifacts }
              .filterNot { it.file in includedFiles }
              .distinctBy { it.id.componentIdentifier to it.file }
              .map { artifact -> createShadowDependency(artifact) }
          }
        }
      )
    }

    val compileClasspathConfig =
      configurations.named(COMPILE_CLASSPATH_CONFIGURATION_NAME) { compileClasspath ->
        compileClasspath.extendsFromCompat(shadowConfig)
      }
    val shadowRuntimeElements =
      configurations.register(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME) { shadowRuntimeElements ->
        shadowRuntimeElements.extendsFromCompat(shadowConfig)
        shadowRuntimeElements.isCanBeConsumed = true
        shadowRuntimeElements.isCanBeResolved = false
        shadowRuntimeElements.attributes { attrs ->
          attrs.attribute(
            Usage.USAGE_ATTRIBUTE,
            objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
          )
          attrs.attribute(
            Category.CATEGORY_ATTRIBUTE,
            objects.named(Category::class.java, Category.LIBRARY),
          )
          attrs.attribute(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            objects.named(LibraryElements::class.java, LibraryElements.JAR),
          )
          attrs.attributeProvider(
            Bundling.BUNDLING_ATTRIBUTE,
            shadow.bundlingAttribute.map { attr -> objects.named(Bundling::class.java, attr) },
          )
        }
        shadowRuntimeElements.outgoing.artifact(tasks.shadowJar)
      }

    // See more details in #2086.
    afterEvaluate {
      if (shadow.addTargetJvmVersionAttribute.get()) {
        // This eager call will lock `toolchain.languageVersion`, so we must defer it by
        // `afterEvaluate`.
        val compileJvmVersion =
          compileClasspathConfig.get().attributes.getAttribute(TARGET_JVM_VERSION_ATTRIBUTE)
        val targetJvmVersion =
          compileJvmVersion ?: javaPluginExtension.targetCompatibility.majorVersion.toInt()
        if (targetJvmVersion != Int.MAX_VALUE) {
          logger.info(
            "Setting target JVM version to {} for {} configuration.",
            targetJvmVersion,
            shadowRuntimeElements.name,
          )
          shadowRuntimeElements
            .get()
            .attributes
            .attribute(TARGET_JVM_VERSION_ATTRIBUTE, targetJvmVersion)
        } else {
          logger.info(
            "Cannot set the target JVM version to Int.MAX_VALUE when `java.autoTargetJvmDisabled` is enabled or in other cases."
          )
        }
      } else {
        logger.info(
          "Skipping setting {} attribute for {} configuration.",
          TARGET_JVM_VERSION_ATTRIBUTE,
          shadowRuntimeElements.name,
        )
      }
    }
  }

  private fun Project.createShadowDependency(artifact: ResolvedArtifact): ModuleDependency {
    val componentIdentifier = artifact.id.componentIdentifier
    val dependency =
      if (
        componentIdentifier is ProjectComponentIdentifier &&
          componentIdentifier.build.buildPath == ":"
      ) {
        dependencies.project(mapOf("path" to componentIdentifier.projectPath)) as ModuleDependency
      } else {
        dependencies.create(artifact.moduleVersion.id.toString()) as ExternalModuleDependency
      }
    dependency.isTransitive = false
    if (dependency is ExternalModuleDependency) {
      dependency.artifact { dependencyArtifact ->
        dependencyArtifact.name = artifact.name
        dependencyArtifact.type = artifact.type
        dependencyArtifact.extension = artifact.extension
        dependencyArtifact.classifier = artifact.classifier
      }
    }
    return dependency
  }

  protected open fun Project.configureComponents() {
    val shadowRuntimeElements = configurations.shadowRuntimeElements
    val shadowComponent = softwareComponentFactory.adhoc(COMPONENT_NAME)
    components.add(shadowComponent)
    @Suppress("UNCHECKED_CAST", "UnstableApiUsage")
    shadowComponent.addVariantsFromConfiguration(
      shadowRuntimeElements as Provider<ConsumableConfiguration>
    ) { variant ->
      variant.mapToMavenScope("runtime")
    }
    components.named("java", AdhocComponentWithVariants::class.java) { component ->
      @Suppress("UNCHECKED_CAST", "UnstableApiUsage")
      component.addVariantsFromConfiguration(
        shadowRuntimeElements as Provider<ConsumableConfiguration>
      ) { variant ->
        variant.mapToOptional()
        if (shadow.addShadowVariantIntoJavaComponent.get()) {
          logger.info("Adding {} variant to Java component.", shadowRuntimeElements.name)
        } else {
          logger.info("Skipping adding {} variant to Java component.", shadowRuntimeElements.name)
          variant.skip()
        }
      }
    }
  }

  protected open fun Project.configureJavaGradlePlugin() {
    moveGradleApiIntoCompileOnly()
  }

  public companion object {
    public const val COMPONENT_NAME: String = SHADOW
    public const val SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME: String = "shadowRuntimeElements"

    @get:JvmSynthetic
    public inline val ConfigurationContainer.shadowRuntimeElements:
      NamedDomainObjectProvider<Configuration>
      get() = named(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME)
  }
}
