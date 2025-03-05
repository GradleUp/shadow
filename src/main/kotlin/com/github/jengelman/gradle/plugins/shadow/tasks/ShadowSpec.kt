package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import kotlin.reflect.KClass
import org.gradle.api.Action
import org.gradle.api.file.CopySpec

@JvmDefaultWithCompatibility
public interface ShadowSpec : CopySpec {
  public fun minimize(): ShadowSpec

  public fun minimize(action: Action<DependencyFilter>?): ShadowSpec

  public fun dependencies(action: Action<DependencyFilter>?): ShadowSpec

  public fun mergeServiceFiles(): ShadowSpec

  public fun mergeServiceFiles(rootPath: String): ShadowSpec

  public fun mergeServiceFiles(action: Action<ServiceFileTransformer>?): ShadowSpec

  public fun mergeGroovyExtensionModules(): ShadowSpec

  public fun append(resourcePath: String): ShadowSpec {
    return append(resourcePath, AppendingTransformer.DEFAULT_SEPARATOR)
  }

  public fun append(resourcePath: String, separator: String): ShadowSpec

  public fun relocate(pattern: String, destination: String): ShadowSpec {
    return relocate(pattern, destination, null)
  }

  public fun relocate(pattern: String, destination: String, action: Action<SimpleRelocator>?): ShadowSpec

  public fun <R : Relocator> relocate(clazz: Class<R>): ShadowSpec {
    return relocate(clazz, null)
  }

  public fun <R : Relocator> relocate(clazz: Class<R>, action: Action<R>?): ShadowSpec

  public fun <R : Relocator> relocate(clazz: KClass<Relocator>): ShadowSpec {
    return relocate(clazz, null)
  }

  public fun <R : Relocator> relocate(clazz: KClass<R>, action: Action<R>?): ShadowSpec

  public fun <R : Relocator> relocate(relocator: R): ShadowSpec {
    return relocate(relocator, null)
  }

  public fun <R : Relocator> relocate(relocator: R, action: Action<R>?): ShadowSpec

  public fun <T : ResourceTransformer> transform(clazz: Class<T>): ShadowSpec {
    return transform(clazz, null)
  }

  public fun <T : ResourceTransformer> transform(clazz: Class<T>, action: Action<T>?): ShadowSpec

  public fun <T : ResourceTransformer> transform(clazz: KClass<T>): ShadowSpec {
    return transform(clazz, null)
  }

  public fun <T : ResourceTransformer> transform(clazz: KClass<T>, action: Action<T>?): ShadowSpec

  public fun <T : ResourceTransformer> transform(transformer: T): ShadowSpec {
    return transform(transformer, null)
  }

  public fun <T : ResourceTransformer> transform(transformer: T, action: Action<T>?): ShadowSpec
}
