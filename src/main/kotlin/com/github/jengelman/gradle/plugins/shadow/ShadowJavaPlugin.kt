package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.SHADOW
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.internal.javaPluginExtension
import com.github.jengelman.gradle.plugins.shadow.internal.runtimeConfiguration
import com.github.jengelman.gradle.plugins.shadow.internal.sourceSets
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.registerShadowJarCommon
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import java.io.File
import java.util.Date
import javax.inject.Inject
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar

public abstract class ShadowJavaPlugin
@Inject
constructor(private val softwareComponentFactory: SoftwareComponentFactory) : Plugin<Project> {

  override fun apply(project: Project): Unit =
    with(project) {
      configureShadowJar()
      configureConfigurations()
      configureComponents()
      @Suppress("DEPRECATION") configureJavaGradlePlugin()
    }

  protected open fun Project.configureShadowJar() {
    val mainSourceSet = sourceSets.named("main")
    val taskProvider =
      registerShadowJarCommon(tasks.named("jar", Jar::class.java)) { task ->
        task.from(mainSourceSet.map { it.output })
        task.generateSourcesJar.convention(
          provider { configurations.findByName(SOURCES_ELEMENTS_CONFIGURATION_NAME) != null }
        )
        task.sourceSetsSourceDirs.convention(
          task.generateSourcesJar.flatMap { generate ->
            if (generate) {
              mainSourceSet.map { it.allSource.sourceDirectories + it.allSource }
            } else {
              provider { emptySet() }
            }
          }
        )
        task.configurations.convention(provider { listOf(runtimeConfiguration) })
      }
    artifacts.add(configurations.shadow.name, taskProvider)
  }

  protected open fun Project.configureConfigurations() {
    val shadowConfig = configurations.shadow
    val compileClasspathConfig =
      configurations.named(COMPILE_CLASSPATH_CONFIGURATION_NAME) { compileClasspath ->
        compileClasspath.extendsFrom(shadowConfig)
      }
    val shadowRuntimeElements =
      registerConsumableConfiguration(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME) {
        extendsFrom(shadowConfig)
        attributes { attrs ->
          attrs.attribute(
            Category.CATEGORY_ATTRIBUTE,
            objects.named(Category::class.java, Category.LIBRARY),
          )
          attrs.attribute(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            objects.named(LibraryElements::class.java, LibraryElements.JAR),
          )
        }
        outgoing.artifact(tasks.shadowJar)
      }
    registerConsumableConfiguration(SHADOW_SOURCES_ELEMENTS_CONFIGURATION_NAME) {
      attributes { attrs ->
        attrs.attribute(
          Category.CATEGORY_ATTRIBUTE,
          objects.named(Category::class.java, Category.DOCUMENTATION),
        )
        attrs.attribute(
          DocsType.DOCS_TYPE_ATTRIBUTE,
          objects.named(DocsType::class.java, DocsType.SOURCES),
        )
      }
      outgoing.artifact(ShadowSourcesPublishArtifact(tasks.shadowJar))
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

  protected open fun Project.configureComponents() {
    val addIntoJavaComponent = shadow.addShadowVariantIntoJavaComponent
    val shadowRuntimeElements = configurations.shadowRuntimeElements
    val shadowSourcesElements = configurations.shadowSourcesElements
    // If `withSourcesJar` is present and `generateSourcesJar` is enabled.
    val sourcesElements = { configurations.findByName(SOURCES_ELEMENTS_CONFIGURATION_NAME) }
    val shouldAddSources = {
      sourcesElements() != null && tasks.shadowJar.flatMap { it.generateSourcesJar }.get()
    }

    val shadowComponent = softwareComponentFactory.adhoc(COMPONENT_NAME)
    components.add(shadowComponent)
    shadowComponent.addVariants(
      outgoingConfiguration = shadowRuntimeElements,
      logger = logger,
    ) {
      mapToMavenScope("runtime")
    }
    shadowComponent.addVariants(
      outgoingConfiguration = shadowSourcesElements,
      logger = logger,
      shouldAdd = shouldAddSources,
    )

    components.named("java", AdhocComponentWithVariants::class.java) { component ->
      component.addVariants(
        outgoingConfiguration = shadowRuntimeElements,
        logger = logger,
        shouldAdd = addIntoJavaComponent::get,
      ) {
        mapToOptional()
      }
      component.addVariants(
        outgoingConfiguration = shadowSourcesElements,
        logger = logger,
        shouldAdd = { addIntoJavaComponent.get() && shouldAddSources() },
      ) {
        mapToOptional()
      }
    }
  }

  private fun AdhocComponentWithVariants.addVariants(
    outgoingConfiguration: NamedDomainObjectProvider<ConsumableConfiguration>,
    logger: Logger,
    shouldAdd: () -> Boolean = { true },
    action: ConfigurationVariantDetails.() -> Unit = {},
  ) {
    addVariantsFromConfiguration(outgoingConfiguration) { variant ->
      if (shouldAdd()) {
        logger.info("Adding {} variant to {} component.", outgoingConfiguration.name, name)
        variant.action()
      } else {
        logger.info("Skipping adding {} variant to {} component.", outgoingConfiguration.name, name)
        variant.skip()
      }
    }
  }

  private fun Project.registerConsumableConfiguration(
    name: String,
    action: ConsumableConfiguration.() -> Unit,
  ) =
    configurations.consumable(name) { configuration ->
      configuration.attributes { attrs ->
        attrs.attribute(
          Usage.USAGE_ATTRIBUTE,
          objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
        )
        attrs.attributeProvider(
          Bundling.BUNDLING_ATTRIBUTE,
          shadow.bundlingAttribute.map { attr -> objects.named(Bundling::class.java, attr) },
        )
      }
      configuration.action()
    }

  @Deprecated("This method will be removed in Shadow 10.")
  protected open fun Project.configureJavaGradlePlugin() {}

  public companion object {
    public const val COMPONENT_NAME: String = SHADOW
    public const val SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME: String = "shadowRuntimeElements"
    public const val SHADOW_SOURCES_ELEMENTS_CONFIGURATION_NAME: String = "shadowSourcesElements"

    @get:JvmSynthetic
    public inline val ConfigurationContainer.shadowRuntimeElements:
      NamedDomainObjectProvider<ConsumableConfiguration>
      get() = named(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME, ConsumableConfiguration::class.java)

    @get:JvmSynthetic
    public inline val ConfigurationContainer.shadowSourcesElements:
      NamedDomainObjectProvider<ConsumableConfiguration>
      get() = named(SHADOW_SOURCES_ELEMENTS_CONFIGURATION_NAME, ConsumableConfiguration::class.java)
  }
}

internal class ShadowSourcesPublishArtifact(private val shadowJarTask: TaskProvider<ShadowJar>) :
  PublishArtifact {
  override fun getName(): String = shadowJarTask.flatMap { it.archiveBaseName }.orNull ?: ""

  override fun getExtension(): String =
    shadowJarTask.flatMap { it.archiveExtension }.orNull ?: "jar"

  override fun getType(): String = "jar"

  override fun getClassifier(): String {
    val shadowClassifier = shadowJarTask.flatMap { it.archiveClassifier }.orNull
    return if (shadowClassifier.isNullOrEmpty()) "sources" else "$shadowClassifier-sources"
  }

  override fun getFile(): File = shadowJarTask.flatMap { it.archiveSourcesFile }.get().asFile

  override fun getDate(): Date? = null

  @Suppress("EagerGradleConfiguration")
  override fun getBuildDependencies(): TaskDependency = TaskDependency {
    setOf(shadowJarTask.get())
  }
}
