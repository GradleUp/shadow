package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.R8Spec
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

internal class DefaultR8Spec(objectFactory: ObjectFactory) : R8Spec {
  override val args: ListProperty<String> =
    objectFactory.listProperty(String::class.java).convention(DEFAULT_ARGS)

  override val keepRules: ListProperty<String> =
    objectFactory.listProperty(String::class.java).convention(emptyList())

  override val keepRuleFiles: ConfigurableFileCollection = objectFactory.fileCollection()

  internal companion object {
    internal const val NO_MINIFICATION_ARG = "--no-minification"
    internal val DEFAULT_ARGS = listOf(NO_MINIFICATION_ARG)
  }
}
