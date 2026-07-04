package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.MinimizeSpec
import com.github.jengelman.gradle.plugins.shadow.tasks.MinimizeTool
import com.github.jengelman.gradle.plugins.shadow.tasks.R8Spec
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

internal open class DefaultMinimizeSpec
@Inject
constructor(
  project: Project,
  objectFactory: ObjectFactory,
) : MinimizeDependencyFilter(project), MinimizeSpec {
  @get:Internal
  internal val r8Spec: DefaultR8Spec by lazy {
    objectFactory.newInstance(DefaultR8Spec::class.java)
  }

  override val tool: Property<MinimizeTool> =
    objectFactory.property(MinimizeTool.DEPENDENCY_ANALYZER)

  @get:Nested
  @get:Optional
  val r8SpecForInputs: R8Spec?
    get() = if (tool.orNull == MinimizeTool.R8) r8Spec else null

  override fun r8(action: Action<in R8Spec>) {
    tool.set(MinimizeTool.R8)
    action.execute(r8Spec)
  }
}
