package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.InputStream
import java.util.jar.JarFile
import java.util.zip.ZipOutputStream
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.util.PatternSet

internal object Utils {

  @JvmStatic
  fun configureRelocation(target: ShadowJar, prefix: String) {
    target.configurations
      .asSequence()
      .flatMap { it.files }
      .flatMap { JarFile(it).entries().asSequence() }
      .filter { it.name.endsWith(".class") && it.name != "module-info.class" }
      .forEach {
        val pkg = it.name.substring(0, it.name.lastIndexOf('/')).replace('/', '.')
        target.relocate(pkg, "$prefix.$pkg")
      }
  }

  @JvmStatic
  fun getRootPatternSet(mainSpec: CopySpecInternal): PatternSet {
    return (mainSpec.buildRootResolver() as DefaultCopySpec.DefaultCopySpecResolver).patternSet
  }

  @JvmStatic
  fun getInternalCompressor(entryCompression: ZipEntryCompression, jar: Jar): ZipCompressor {
    return when (entryCompression) {
      ZipEntryCompression.DEFLATED -> DefaultZipCompressor(jar.isZip64, ZipOutputStream.DEFLATED)
      ZipEntryCompression.STORED -> DefaultZipCompressor(jar.isZip64, ZipOutputStream.STORED)
      else -> throw IllegalArgumentException("Unknown Compression type $entryCompression")
    }
  }

  fun requireResourceAsText(name: String): String {
    return requireResourceAsStream(name).bufferedReader().readText()
  }

  private fun requireResourceAsStream(name: String): InputStream {
    return this::class.java.getResourceAsStream(name) ?: error("Resource $name not found.")
  }
}
