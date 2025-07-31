package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.gradle.api.Action

public interface ShadowSpec {
  public fun minimize()

  public fun minimize(action: Action<DependencyFilter>)

  public fun dependencies(action: Action<DependencyFilter>)

  public fun mergeServiceFiles()

  public fun mergeServiceFiles(rootPath: String)

  public fun mergeServiceFiles(action: Action<ServiceFileTransformer>)

  public fun mergeGroovyExtensionModules()

  public fun append(resourcePath: String) {
    append(resourcePath, AppendingTransformer.DEFAULT_SEPARATOR)
  }

  public fun append(resourcePath: String, separator: String)

  public fun relocate(pattern: String, destination: String) {
    relocate(pattern, destination, action = {})
  }

  public fun relocate(pattern: String, destination: String, action: Action<SimpleRelocator>)

  public fun <R : Relocator> relocate(clazz: Class<R>) {
    relocate(clazz, action = {})
  }

  public fun <R : Relocator> relocate(clazz: Class<R>, action: Action<R>)

  public fun <R : Relocator> relocate(relocator: R) {
    relocate(relocator, action = {})
  }

  public fun <R : Relocator> relocate(relocator: R, action: Action<R>)

  public fun <T : ResourceTransformer> transform(clazz: Class<T>) {
    transform(clazz, action = {})
  }

  public fun <T : ResourceTransformer> transform(clazz: Class<T>, action: Action<T>)

  public fun <T : ResourceTransformer> transform(transformer: T) {
    transform(transformer, action = {})
  }

  public fun <T : ResourceTransformer> transform(transformer: T, action: Action<T>)
}
