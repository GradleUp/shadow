package com.github.jengelman.gradle.plugins.shadow.tasks

import java.io.Serializable
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec

// DependencyFilter is used as Gradle Input in ShadowJar, so it must be Serializable.
public interface DependencyFilter : Serializable {
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
  public fun exclude(spec: Spec<ResolvedDependency>): DependencyFilter

  /**
   * Include dependencies that match the provided [spec].
   */
  public fun include(spec: Spec<ResolvedDependency>): DependencyFilter

  /**
   * Create a [Spec] that matches the provided project [notation].
   */
  public fun project(notation: Map<String, *>): Spec<ResolvedDependency>

  /**
   * Create a [Spec] that matches the provided project [path].
   */
  public fun project(path: String): Spec<ResolvedDependency>

  /**
   * Create a [Spec] that matches the provided [dependencyNotation].
   */
  public fun dependency(dependencyNotation: Any): Spec<ResolvedDependency>

  /**
   * Create a [Spec] that matches the provided [dependency].
   */
  public fun dependency(dependency: Dependency): Spec<ResolvedDependency>
}
