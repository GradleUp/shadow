package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import org.gradle.api.Action
import org.gradle.api.file.CopySpec

@JvmDefaultWithCompatibility
public interface ShadowSpec : CopySpec {
  public fun minimize(): ShadowSpec

  public fun minimize(action: Action<DependencyFilter>?): ShadowSpec

  public fun dependencies(action: Action<DependencyFilter>?): ShadowSpec

  public fun <T : ResourceTransformer> transform(clazz: Class<T>): ShadowSpec {
    return transform(clazz, null)
  }

  @Throws(
    InstantiationException::class,
    IllegalAccessException::class,
    NoSuchMethodException::class,
    InvocationTargetException::class,
  )
  public fun <T : ResourceTransformer> transform(clazz: Class<T>, action: Action<T>?): ShadowSpec

  @Throws(
    InstantiationException::class,
    IllegalAccessException::class,
    NoSuchMethodException::class,
    InvocationTargetException::class,
  )
  public fun <T : ResourceTransformer> transform(clazz: KClass<T>): ShadowSpec {
    return transform(clazz, null)
  }

  @Throws(
    InstantiationException::class,
    IllegalAccessException::class,
    NoSuchMethodException::class,
    InvocationTargetException::class,
  )
  public fun <T : ResourceTransformer> transform(clazz: KClass<T>, action: Action<T>?): ShadowSpec

  public fun <T : ResourceTransformer> transform(transformer: T): ShadowSpec {
    return transform(transformer, null)
  }

  public fun <T : ResourceTransformer> transform(transformer: T, action: Action<T>?): ShadowSpec

  public fun mergeServiceFiles(): ShadowSpec

  public fun mergeServiceFiles(rootPath: String): ShadowSpec

  public fun mergeServiceFiles(action: Action<ServiceFileTransformer>?): ShadowSpec

  public fun mergeGroovyExtensionModules(): ShadowSpec

  public fun append(resourcePath: String): ShadowSpec

  public fun append(resourcePath: String, separator: String): ShadowSpec

  public fun relocate(pattern: String, destination: String): ShadowSpec

  public fun relocate(pattern: String, destination: String, action: Action<SimpleRelocator>?): ShadowSpec

  public fun relocate(relocator: Relocator): ShadowSpec

  @Throws(
    InstantiationException::class,
    IllegalAccessException::class,
    NoSuchMethodException::class,
    InvocationTargetException::class,
  )
  public fun relocate(clazz: Class<Relocator>): ShadowSpec

  @Throws(
    InstantiationException::class,
    IllegalAccessException::class,
    NoSuchMethodException::class,
    InvocationTargetException::class,
  )
  public fun <R : Relocator> relocate(clazz: Class<R>, action: Action<R>?): ShadowSpec
}
