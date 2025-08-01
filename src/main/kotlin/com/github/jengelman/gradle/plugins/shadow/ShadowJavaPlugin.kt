package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.jar
import com.github.jengelman.gradle.plugins.shadow.internal.javaPluginExtension
import com.github.jengelman.gradle.plugins.shadow.internal.runtimeConfiguration
import com.github.jengelman.gradle.plugins.shadow.internal.sourceSets
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin

public abstract class ShadowJavaPlugin @Inject constructor(
  private val softwareComponentFactory: SoftwareComponentFactory,
) : Plugin<Project> {

  override fun apply(project: Project): Unit = with(project) {
    configureShadowJar()
    configureConfigurations()
    configureComponents()
    configureJavaGradlePlugin()
  }

  protected open fun Project.configureShadowJar() {
    val jarTask = tasks.jar
    val taskProvider = registerShadowJarCommon { task ->
      @Suppress("EagerGradleConfiguration") // mergeSpec.from hasn't supported lazy configuration yet.
      task.manifest.inheritFrom(jarTask.get().manifest)
      val attrProvider = jarTask.map { it.manifest.attributes[classPathAttributeKey]?.toString().orEmpty() }
      val files = files(configurations.shadow)
      task.doFirst {
        if (!files.isEmpty) {
          val attrs = listOf(attrProvider.getOrElse("")) + files.map { it.name }
          task.manifest.attributes[classPathAttributeKey] = attrs.joinToString(" ").trim()
        }
      }
      task.from(sourceSets.named("main").map { it.output })
      task.configurations.convention(provider { listOf(runtimeConfiguration) })
    }
    artifacts.add(configurations.shadow.name, taskProvider)
  }

  protected open fun Project.configureConfigurations() {
    val shadowConfiguration = configurations.shadow.get()
    configurations.named(COMPILE_CLASSPATH_CONFIGURATION_NAME) { compileClasspath ->
      compileClasspath.extendsFrom(shadowConfiguration)
    }
    @Suppress("EagerGradleConfiguration") // this should be created eagerly.
    configurations.create(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME) {
      it.extendsFrom(shadowConfiguration)
      it.isCanBeConsumed = true
      it.isCanBeResolved = false
      it.attributes { attr ->
        attr.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attr.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attr.attribute(
          LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
          objects.named(LibraryElements::class.java, LibraryElements.JAR),
        )
        attr.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.SHADOWED))
        val targetJvmVersion = provider {
          javaPluginExtension.targetCompatibility.majorVersion.toInt()
        }
        // Track JavaPluginExtension to update targetJvmVersion when it changes.
        attr.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJvmVersion)
      }
      it.outgoing.artifact(tasks.shadowJar)
    }
  }

  protected open fun Project.configureComponents() {
    val shadowRuntimeElements = configurations.shadowRuntimeElements.get()
    components.named("java", AdhocComponentWithVariants::class.java) {
      it.addVariantsFromConfiguration(shadowRuntimeElements) { variant ->
        variant.mapToOptional()
      }
    }
    val shadowComponent = softwareComponentFactory.adhoc(ShadowBasePlugin.COMPONENT_NAME)
    components.add(shadowComponent)
    shadowComponent.addVariantsFromConfiguration(shadowRuntimeElements) { variant ->
      variant.mapToMavenScope("runtime")
    }
  }

  protected open fun Project.configureJavaGradlePlugin() {
    plugins.withType(JavaGradlePluginPlugin::class.java).configureEach {
      val gradleApi = dependencies.gradleApi()
      // Remove the gradleApi so it isn't merged into the jar file.
      // This is required because 'java-gradle-plugin' adds gradleApi() to the 'api' configuration.
      // See https://github.com/gradle/gradle/blob/972c3e5c6ef990dd2190769c1ce31998a9402a79/subprojects/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/JavaGradlePluginPlugin.java#L161.
      configurations.named(API_CONFIGURATION_NAME) { api ->
        // Only proceed if the removal is successful.
        if (!api.dependencies.remove(gradleApi)) return@named
        // Compile only gradleApi() to make sure the plugin can compile against Gradle API.
        configurations.getByName(COMPILE_ONLY_CONFIGURATION_NAME)
          .dependencies.add(dependencies.gradleApi())
      }
    }
  }

  public companion object {
    public const val SHADOW_JAR_TASK_NAME: String = "shadowJar"
    public const val SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME: String = "shadowRuntimeElements"

    public inline val TaskContainer.shadowJar: TaskProvider<ShadowJar>
      get() = named(SHADOW_JAR_TASK_NAME, ShadowJar::class.java)

    public inline val ConfigurationContainer.shadowRuntimeElements: NamedDomainObjectProvider<Configuration>
      get() = named(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME)

    @JvmStatic
    public fun Project.registerShadowJarCommon(
      action: Action<ShadowJar>,
    ): TaskProvider<ShadowJar> {
      return tasks.register(SHADOW_JAR_TASK_NAME, ShadowJar::class.java) { task ->
        task.group = LifecycleBasePlugin.BUILD_GROUP
        task.description = "Create a combined JAR of project and runtime dependencies"
        task.archiveClassifier.set("all")
        task.exclude(
          "META-INF/INDEX.LIST",
          "META-INF/*.SF",
          "META-INF/*.DSA",
          "META-INF/*.RSA",
          // module-info.class in Multi-Release folders.
          "META-INF/versions/**/module-info.class",
          "module-info.class",
        )
        @Suppress("EagerGradleConfiguration") // Can't use `named` as the task is optional.
        tasks.findByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)?.dependsOn(task)
        action.execute(task)
      }
    }
  }
}
