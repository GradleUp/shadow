package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import java.lang.reflect.InvocationTargetException
import org.gradle.api.Action
import org.gradle.api.file.CopySpec

internal interface ShadowSpec : CopySpec {
  fun minimize(): ShadowSpec

  fun minimize(configureClosure: Action<DependencyFilter>): ShadowSpec

  fun dependencies(configure: Action<DependencyFilter>): ShadowSpec

  @Throws(
    InstantiationException::class,
    IllegalAccessException::class,
    NoSuchMethodException::class,
    InvocationTargetException::class,
  )
  fun transform(clazz: Class<out Transformer>): ShadowSpec

  @Throws(
    InstantiationException::class,
    IllegalAccessException::class,
    NoSuchMethodException::class,
    InvocationTargetException::class,
  )
  fun <T : Transformer> transform(clazz: Class<T>, configure: Action<T>): ShadowSpec

  fun transform(transformer: Transformer): ShadowSpec

  fun mergeServiceFiles(): ShadowSpec

  fun mergeServiceFiles(rootPath: String): ShadowSpec

  fun mergeServiceFiles(configureClosure: Action<ServiceFileTransformer>): ShadowSpec

  fun mergeGroovyExtensionModules(): ShadowSpec

  fun append(resourcePath: String): ShadowSpec

  fun relocate(pattern: String, destination: String): ShadowSpec

  fun relocate(pattern: String, destination: String, configure: Action<SimpleRelocator>): ShadowSpec

  fun relocate(relocator: Relocator): ShadowSpec

  @Throws(
    InstantiationException::class,
    IllegalAccessException::class,
    NoSuchMethodException::class,
    InvocationTargetException::class,
  )
  fun relocate(clazz: Class<out Relocator>): ShadowSpec

  @Throws(
    InstantiationException::class,
    IllegalAccessException::class,
    NoSuchMethodException::class,
    InvocationTargetException::class,
  )
  fun <R : Relocator> relocate(clazz: Class<R>, configure: Action<R>): ShadowSpec

  val stats: ShadowStats
}
