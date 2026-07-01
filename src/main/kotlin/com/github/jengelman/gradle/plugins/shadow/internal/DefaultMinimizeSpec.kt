package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.MinimizeSpec
import com.github.jengelman.gradle.plugins.shadow.tasks.MinimizeTool
import com.github.jengelman.gradle.plugins.shadow.tasks.R8Spec
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

internal class DefaultMinimizeSpec(project: Project, objectFactory: ObjectFactory) :
  MinimizeDependencyFilter(project), MinimizeSpec {
  override val tool: Property<MinimizeTool> =
    objectFactory.property(MinimizeTool.DEPENDENCY_ANALYZER)

  private val r8Spec: DefaultR8Spec by lazy { DefaultR8Spec(objectFactory) }

  @get:Nested
  @get:Optional
  val r8SpecForInputs: R8Spec?
    get() = if (tool.orNull == MinimizeTool.R8) r8Spec else null

  internal fun r8Spec(): DefaultR8Spec = r8Spec

  override fun r8(action: Action<in R8Spec>) {
    tool.set(MinimizeTool.R8)
    action.execute(r8Spec)
  }

  override fun r8(action: Closure<*>) {
    tool.set(MinimizeTool.R8)
    val previousDelegate = action.delegate
    val previousResolveStrategy = action.resolveStrategy
    try {
      action.delegate = r8Spec
      action.resolveStrategy = Closure.DELEGATE_FIRST
      action.call(r8Spec)
    } finally {
      action.delegate = previousDelegate
      action.resolveStrategy = previousResolveStrategy
    }
  }
}
