package com.github.jengelman.gradle.plugins.shadow.util

import assertk.Assert
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import assertk.assertions.containsNone
import assertk.assertions.containsOnly
import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * A wrapper for [JarFile] that also implements [Path].
 *
 * We must declare some functions like [kotlin.io.path.deleteExisting] explicitly as they could not
 * be delegated to [JarPath] type.
 */
@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
class JarPath(val path: Path) :
  JarFile(path.toFile()),
  Path by path {

  val mainAttrSize: Int get() = manifest.mainAttributes.size

  fun getMainAttr(name: String): String? {
    return manifest.mainAttributes.getValue(name)
  }
}

fun ZipFile.getContent(entryName: String): String {
  return getStream(entryName).bufferedReader().use { it.readText() }
}

fun ZipFile.getStream(entryName: String): InputStream {
  val entry = requireNotNull(getEntry(entryName)) { "Entry $entryName not found in all entries: ${entries().toList()}" }
  return getInputStream(entry)
}

fun Assert<JarPath>.getContent(entryName: String) = transform { it.getContent(entryName) }

fun Assert<JarPath>.getMainAttr(name: String) = transform { it.getMainAttr(name) }

fun Assert<JarPath>.containsEntries(vararg entries: String) = transform { actual ->
  actual.entries().toList().map { it.name }
}.containsAtLeast(*entries)

fun Assert<JarPath>.doesNotContainEntries(vararg entries: String) = transform { actual ->
  actual.entries().toList().map { it.name }
}.containsNone(*entries)

fun Assert<JarPath>.containsFileEntriesOnly(vararg entries: String) = transform { actual ->
  actual.entries().toList().filter { !it.isDirectory }.map { it.name }
}.containsOnly(*entries)
