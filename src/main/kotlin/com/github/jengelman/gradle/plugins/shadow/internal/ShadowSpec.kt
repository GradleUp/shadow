package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.DependencyFilter
import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import java.lang.reflect.InvocationTargetException
import org.gradle.api.Action
import org.gradle.api.file.CopySpec

internal interface ShadowSpec : CopySpec {
    fun minimize(): ShadowSpec

    fun minimize(action: Action<DependencyFilter>?): ShadowSpec

    fun dependencies(action: Action<DependencyFilter>?): ShadowSpec

    @Throws(
        InstantiationException::class,
        IllegalAccessException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class,
    )
    fun <T : Transformer> transform(clazz: Class<T>): ShadowSpec

    @Throws(
        InstantiationException::class,
        IllegalAccessException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class,
    )
    fun <T : Transformer> transform(clazz: Class<T>, action: Action<T>?): ShadowSpec

    fun transform(transformer: Transformer): ShadowSpec

    fun mergeServiceFiles(): ShadowSpec

    fun mergeServiceFiles(rootPath: String): ShadowSpec

    fun mergeServiceFiles(action: Action<ServiceFileTransformer>?): ShadowSpec

    fun mergeGroovyExtensionModules(): ShadowSpec

    fun append(resourcePath: String): ShadowSpec

    fun relocate(pattern: String, destination: String): ShadowSpec

    fun relocate(pattern: String, destination: String, action: Action<SimpleRelocator>?): ShadowSpec

    fun relocate(relocator: Relocator): ShadowSpec

    @Throws(
        InstantiationException::class,
        IllegalAccessException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class,
    )
    fun relocate(clazz: Class<Relocator>): ShadowSpec

    @Throws(
        InstantiationException::class,
        IllegalAccessException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class,
    )
    fun <R : Relocator> relocate(clazz: Class<R>, action: Action<R>?): ShadowSpec

    val stats: ShadowStats
}
