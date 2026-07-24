@file:Suppress("DEPRECATION")

package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/** Configures how [ShadowJar.minimize] removes unused code from the shadowed JAR. */
@Deprecated(message = "This compatibility layer will be removed in Shadow 10.")
public interface MinimizeSpec : DependencyFilter {
  /**
   * The tool used to minimize the shadowed JAR.
   *
   * Defaults to [MinimizeTool.DEPENDENCY_ANALYZER].
   */
  @Deprecated(
    message =
      "Configure R8 with `ShadowJar.r8` instead. This compatibility property will be removed in Shadow 10."
  )
  @get:Input
  public val tool: Property<MinimizeTool>

  /** Use R8 to post-process the shadowed JAR and configure its options. */
  @Deprecated(
    message =
      "Use the standalone `ShadowJar.r8` block instead. This compatibility DSL will be removed in Shadow 10."
  )
  public fun r8(action: Action<in R8Spec>)
}
