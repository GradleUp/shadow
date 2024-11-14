package com.github.jengelman.gradle.plugins.shadow.internal

import groovy.lang.Closure
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec

public interface DependencyFilter {
  /**
   * Resolve a FileCollection against the include/exclude rules in the filter.
   */
  public fun resolve(configuration: FileCollection): FileCollection

  /**
   * Resolve all FileCollections against the include/exclude ruels in the filter and combine the results.
   */
  public fun resolve(configurations: MutableCollection<FileCollection>): FileCollection

  /**
   * Exclude dependencies that match the provided spec.
   */
  public fun exclude(spec: Spec<ResolvedDependency>): DependencyFilter

  /**
   * Include dependencies that match the provided spec.
   */
  public fun include(spec: Spec<ResolvedDependency>): DependencyFilter

  /**
   * Create a spec that matches the provided project notation on group, name, and version.
   */
  public fun project(notation: Map<String, *>): Spec<ResolvedDependency>

  /**
   * Create a spec that matches the default configuration for the provided project path on group, name, and version.
   */
  public fun project(notation: String): Spec<ResolvedDependency>

  /**
   * Create a spec that matches dependencies using the provided notation on group, name, and version.
   */
  public fun dependency(notation: Any): Spec<ResolvedDependency>

  /**
   * Create a spec that matches the provided dependency on group, name, and version.
   */
  public fun dependency(dependency: Dependency): Spec<ResolvedDependency>

  /**
   * Create a spec that matches the provided closure.
   */
  public fun dependency(closure: Closure<*>): Spec<ResolvedDependency>
}
