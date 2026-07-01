package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.R8Spec
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

internal class DefaultR8Spec(objectFactory: ObjectFactory) : R8Spec {
  private val defaultArgs: ListProperty<String> =
    objectFactory.listProperty(String::class.java).convention(DEFAULT_ARGS)

  @get:Input
  val obfuscationEnabled: Property<Boolean> =
    objectFactory.property(Boolean::class.java).convention(false)

  @get:Input
  val optimizationEnabled: Property<Boolean> =
    objectFactory.property(Boolean::class.java).convention(false)

  override val args: ListProperty<String> =
    objectFactory.listProperty(String::class.java).convention(defaultArgs)

  override val keepRules: ListProperty<String> =
    objectFactory.listProperty(String::class.java).convention(emptyList())

  override val keepRuleFiles: ConfigurableFileCollection = objectFactory.fileCollection()

  override fun enableObfuscation() {
    defaultArgs.set(emptyList())
    obfuscationEnabled.set(true)
  }

  override fun enableOptimization() {
    optimizationEnabled.set(true)
  }

  internal companion object {
    internal const val NO_MINIFICATION_ARG = "--no-minification"
    internal const val DONT_OPTIMIZE_RULE = "-dontoptimize"
    internal val DEFAULT_ARGS = listOf(NO_MINIFICATION_ARG)
  }
}
