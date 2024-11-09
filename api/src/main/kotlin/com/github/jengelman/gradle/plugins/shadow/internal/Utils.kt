@file:JvmName("Utils")

package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.InputStream
import java.util.jar.JarFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.util.PatternSet

public fun getRootPatternSet(mainSpec: CopySpecInternal): PatternSet {
  return (mainSpec.buildRootResolver() as DefaultCopySpec.DefaultCopySpecResolver).patternSet
}

public fun getInternalCompressor(entryCompression: ZipEntryCompression, jar: Jar): ZipCompressor {
  return when (entryCompression) {
    ZipEntryCompression.DEFLATED -> DefaultZipCompressor(jar.isZip64, ZipOutputStream.DEFLATED)
    ZipEntryCompression.STORED -> DefaultZipCompressor(jar.isZip64, ZipOutputStream.STORED)
  }
}

public fun configureRelocation(target: ShadowJar, prefix: String) {
  val packages = mutableSetOf<String>()
  target.configurations.forEach { configuration ->
    configuration.files.forEach { jarFile ->
      JarFile(jarFile).use { jf ->
        jf.entries().asSequence().forEach { entry ->
          if (entry.name.endsWith(".class") && entry.name != "module-info.class") {
            packages.add(entry.name.substringBeforeLast('/').replace('/', '.'))
          }
        }
      }
    }
  }
  packages.forEach {
    target.relocate(it, "$prefix.$it")
  }
}

internal inline fun <R> runOrThrow(
  error: (e: Exception) -> Nothing = { throw RuntimeException(it) },
  block: () -> R,
): R {
  return try {
    block()
  } catch (e: Exception) {
    error(e)
  }
}

internal fun Class<*>.requireResourceAsText(name: String): String {
  return requireResourceAsStream(name).bufferedReader().readText()
}

private fun Class<*>.requireResourceAsStream(name: String): InputStream {
  return getResourceAsStream(name) ?: error("Resource $name not found.")
}
