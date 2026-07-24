@file:Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")

package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.DependencyFilter
import com.github.jengelman.gradle.plugins.shadow.tasks.MinimizeSpec
import com.github.jengelman.gradle.plugins.shadow.tasks.MinimizeTool
import com.github.jengelman.gradle.plugins.shadow.tasks.R8Spec
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal

internal open class DefaultMinimizeSpec
@Inject
constructor(
  project: Project,
  objectFactory: ObjectFactory,
  @get:Internal internal val r8Spec: DefaultR8Spec,
) : MinimizeSpec, DependencyFilter by MinimizeDependencyFilter(project) {
  override val tool: Property<MinimizeTool> =
    objectFactory.property(MinimizeTool.DEPENDENCY_ANALYZER)

  override fun r8(action: Action<in R8Spec>) {
    tool.set(MinimizeTool.R8)
    action.execute(r8Spec)
  }
}
