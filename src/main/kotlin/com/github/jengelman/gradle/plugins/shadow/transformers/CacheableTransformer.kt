package com.github.jengelman.gradle.plugins.shadow.transformers

/**
 * Marks that a given instance of [Transformer] is compatible with the Gradle build cache.
 * In other words, it has its appropriate inputs annotated so that Gradle can consider them when
 * determining the cache key.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class CacheableTransformer
