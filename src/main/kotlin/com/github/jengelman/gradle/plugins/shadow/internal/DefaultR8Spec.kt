package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.R8Spec
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

internal open class DefaultR8Spec @Inject constructor(objectFactory: ObjectFactory) : R8Spec {
  private val defaultArgs: ListProperty<String> = objectFactory.listProperty(DEFAULT_ARGS)

  @get:Input val obfuscationEnabled: Property<Boolean> = objectFactory.property(false)

  @get:Input val optimizationEnabled: Property<Boolean> = objectFactory.property(false)

  override val args: ListProperty<String> = objectFactory.listProperty(defaultArgs)

  override val proguardRules: ListProperty<String> = objectFactory.listProperty()

  override val proguardRuleFiles: ConfigurableFileCollection = objectFactory.fileCollection()

  override fun enableObfuscation() {
    defaultArgs.set(emptyList())
    obfuscationEnabled.set(true)
  }

  override fun enableOptimization() {
    optimizationEnabled.set(true)
  }

  internal companion object {
    const val NO_MINIFICATION_ARG = "--no-minification"
    const val DONT_OPTIMIZE_RULE = "-dontoptimize"
    val DEFAULT_ARGS = listOf(NO_MINIFICATION_ARG)
  }
}
