package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin

public abstract class ShadowJavaPlugin @Inject constructor(
  private val softwareComponentFactory: SoftwareComponentFactory,
) : Plugin<Project> {

  override fun apply(project: Project) {
    val shadowConfiguration = project.configurations.getByName(ShadowBasePlugin.CONFIGURATION_NAME)
    val shadowTaskProvider = configureShadowTask(project, shadowConfiguration)

    project.configurations.named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME) {
      it.extendsFrom(shadowConfiguration)
    }

    val shadowRuntimeElements = project.configurations.create(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME) {
      it.extendsFrom(shadowConfiguration)
      it.isCanBeConsumed = true
      it.isCanBeResolved = false
      it.attributes { attr ->
        attr.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attr.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
        attr.attribute(
          LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
          project.objects.named(LibraryElements::class.java, LibraryElements.JAR),
        )
        attr.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling::class.java, Bundling.SHADOWED))
      }
      it.outgoing.artifact(shadowTaskProvider)
    }

    project.components.named("java", AdhocComponentWithVariants::class.java) {
      it.addVariantsFromConfiguration(shadowRuntimeElements) { variant ->
        variant.mapToOptional()
      }
    }

    val shadowComponent = softwareComponentFactory.adhoc(ShadowBasePlugin.COMPONENT_NAME)
    project.components.add(shadowComponent)
    shadowComponent.addVariantsFromConfiguration(shadowRuntimeElements) { variant ->
      variant.mapToMavenScope("runtime")
    }

    project.plugins.withType(JavaGradlePluginPlugin::class.java).configureEach {
      // Remove the gradleApi so it isn't merged into the jar file.
      // This is required because 'java-gradle-plugin' adds gradleApi() to the 'api' configuration.
      // See https://github.com/gradle/gradle/blob/972c3e5c6ef990dd2190769c1ce31998a9402a79/subprojects/plugin-development/src/main/java/org/gradle/plugin/de
      project.configurations.named(JavaPlugin.API_CONFIGURATION_NAME) {
        it.dependencies.remove(project.dependencies.gradleApi())
      }
      // Compile only gradleApi() to make sure the plugin can compile against Gradle API.
      project.configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
        it.dependencies.add(project.dependencies.gradleApi())
      }
    }
  }

  private fun configureShadowTask(project: Project, shadowConfiguration: Configuration): TaskProvider<ShadowJar> {
    val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
    val jarTask = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)
    val taskProvider = project.tasks.register(SHADOW_JAR_TASK_NAME, ShadowJar::class.java) { shadow ->
      shadow.group = ShadowBasePlugin.GROUP_NAME
      shadow.description = "Create a combined JAR of project and runtime dependencies"
      shadow.archiveClassifier.set("all")
      @Suppress("EagerGradleConfiguration")
      shadow.manifest.inheritFrom(jarTask.get().manifest)
      val attrProvider = jarTask.map { it.manifest.attributes["Class-Path"]?.toString().orEmpty() }
      val files = project.objects.fileCollection().from(shadowConfiguration)
      shadow.doFirst {
        if (!files.isEmpty) {
          val attrs = listOf(attrProvider.getOrElse("")) + files.map { it.name }
          shadow.manifest.attributes["Class-Path"] = attrs.joinToString(" ").trim()
        }
      }
      shadow.from(sourceSets.getByName("main").output)
      shadow.configurations = listOf(
        project.configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
          ?: project.configurations.getByName("runtime"),
      )
      shadow.exclude(
        "META-INF/INDEX.LIST",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "module-info.class",
      )
    }
    project.artifacts.add(shadowConfiguration.name, taskProvider)
    return taskProvider
  }

  public companion object {
    public const val SHADOW_JAR_TASK_NAME: String = "shadowJar"
    public const val SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME: String = "shadowRuntimeElements"
  }
}
