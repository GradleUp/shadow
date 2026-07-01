package com.github.jengelman.gradle.plugins.shadow.tasks

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested

/** Configures how [ShadowJar.minimize] removes unused code from the shadowed JAR. */
public interface MinimizeSpec : DependencyFilter {
  /**
   * The tool used to minimize the shadowed JAR.
   *
   * Defaults to [MinimizeTool.DEPENDENCY_ANALYZER].
   */
  @get:Input public val tool: Property<MinimizeTool>

  /** R8-specific minimization options. */
  @get:Nested public val r8Spec: R8Spec

  /** Use R8 to minimize the shadowed JAR and configure its options. */
  public fun r8(action: Action<in R8Spec>)

  /** Use R8 to minimize the shadowed JAR and configure its options. */
  public fun r8(action: Closure<*>)
}
