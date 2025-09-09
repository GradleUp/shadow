package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.internal.property
import java.io.Serializable
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

// DependencyFilter is used as Gradle Input in ShadowJar, so it must be Serializable.
public interface DependencyFilter : Serializable {
  /**
   * When `true`, excluded dependencies will be added to the `shadow` configuration.
   * This is useful when you want to exclude a dependency from the shadowed JAR, but still want it to be available at
   * the `Class-Path` manifest attribute.
   *
   * Default is `false`.
   */
  @get:Input
  public val addExcludedIntoShadowConfiguration: Property<Boolean>

  @get:Classpath
  public val excludedDependencies: ConfigurableFileCollection

  /**
   * Resolve a [configuration] against the [include]/[exclude] rules in the filter.
   */
  public fun resolve(configuration: Configuration): FileCollection

  /**
   * Resolve all [configurations] against the [include]/[exclude] rules in the filter and combine the results.
   */
  public fun resolve(configurations: Collection<Configuration>): FileCollection

  /**
   * Exclude dependencies that match the provided [spec].
   */
  public fun exclude(spec: Spec<ResolvedDependency>)

  /**
   * Include dependencies that match the provided [spec].
   */
  public fun include(spec: Spec<ResolvedDependency>)

  /**
   * Create a [Spec] that matches the provided project [notation].
   */
  public fun project(notation: Any): Spec<ResolvedDependency>

  /**
   * Create a [Spec] that matches the provided [dependencyNotation].
   */
  public fun dependency(dependencyNotation: Any): Spec<ResolvedDependency>

  public abstract class AbstractDependencyFilter(
    @Transient private val project: Project,
    @Transient protected val includeSpecs: MutableList<Spec<ResolvedDependency>> = mutableListOf(),
    @Transient protected val excludeSpecs: MutableList<Spec<ResolvedDependency>> = mutableListOf(),
    @Transient override val addExcludedIntoShadowConfiguration: Property<Boolean> = project.objects.property(false),
    @Transient override val excludedDependencies: ConfigurableFileCollection = project.objects.fileCollection(),
  ) : DependencyFilter {

    protected abstract fun resolve(
      dependencies: Set<ResolvedDependency>,
      includedDependencies: MutableSet<ResolvedDependency>,
      excludedDependencies: MutableSet<ResolvedDependency>,
    )

    override fun resolve(configuration: Configuration): FileCollection {
      val includes = mutableSetOf<ResolvedDependency>()
      val excludes = mutableSetOf<ResolvedDependency>()
      resolve(
        dependencies = configuration.resolvedConfiguration.firstLevelModuleDependencies,
        includedDependencies = includes,
        excludedDependencies = excludes,
      )
      excludedDependencies.from(excludes.flatMap { it.moduleArtifacts.map(ResolvedArtifact::getFile) })
      return project.files(configuration.files) - excludedDependencies
    }

    override fun resolve(configurations: Collection<Configuration>): FileCollection {
      return configurations.map { resolve(it) }
        .reduceOrNull { acc, fileCollection -> acc + fileCollection }
        ?: project.files()
    }

    override fun exclude(spec: Spec<ResolvedDependency>) {
      excludeSpecs.add(spec)
    }

    override fun include(spec: Spec<ResolvedDependency>) {
      includeSpecs.add(spec)
    }

    override fun project(notation: Any): Spec<ResolvedDependency> {
      @Suppress("UNCHECKED_CAST")
      val realNotation = when (notation) {
        is ProjectDependency -> return notation.toSpec()
        is Provider<*> -> mapOf("path" to notation.get())
        is String -> mapOf("path" to notation)
        is Map<*, *> -> notation as Map<String, Any>
        else -> throw IllegalArgumentException("Unsupported notation type: ${notation::class.java}")
      }
      return project.dependencies.project(realNotation).toSpec()
    }

    override fun dependency(dependencyNotation: Any): Spec<ResolvedDependency> {
      val realNotation = when (dependencyNotation) {
        is Provider<*> -> dependencyNotation.get()
        else -> dependencyNotation
      }
      return project.dependencies.create(realNotation).toSpec()
    }

    protected fun ResolvedDependency.isIncluded(): Boolean {
      val include = includeSpecs.isEmpty() || includeSpecs.any { it.isSatisfiedBy(this) }
      val exclude = excludeSpecs.isNotEmpty() && excludeSpecs.any { it.isSatisfiedBy(this) }
      return include && !exclude
    }

    private fun Dependency.toSpec(): Spec<ResolvedDependency> {
      return Spec<ResolvedDependency> { resolvedDependency ->
        val groupMatch = group?.let {
          it == resolvedDependency.moduleGroup || resolvedDependency.moduleGroup.matches(it.toRegex())
        } ?: true
        val nameMatch = name.let {
          it == resolvedDependency.moduleName || resolvedDependency.moduleName.matches(it.toRegex())
        }
        val versionMatch = version?.let {
          // Version like `1.0.0+1` can't be converted to regex directly because `+` is a special character in regex.
          // So we check for exact match first, then fallback to regex match.
          it == resolvedDependency.moduleVersion || resolvedDependency.moduleVersion.matches(it.toRegex())
        } ?: true

        groupMatch && nameMatch && versionMatch
      }
    }
  }
}
