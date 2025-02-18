package com.github.jengelman.gradle.plugins.shadow.util

import assertk.Assert
import assertk.fail
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.Spliterator
import java.util.function.Consumer
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * A wrapper for [JarFile] that also implements [Path].
 *
 * We must declare some functions like [kotlin.io.path.deleteExisting] explicitly as they could not
 * be delegated to [JarPath] type.
 */
class JarPath(val path: Path) :
  JarFile(path.toFile()),
  Path by path {

  fun getMainAttr(name: String): String? {
    return manifest.mainAttributes.getValue(name)
  }

  override fun startsWith(other: String): Boolean {
    return path.startsWith(other)
  }

  override fun endsWith(other: String): Boolean {
    return path.endsWith(other)
  }

  override fun resolve(other: String): Path {
    return path.resolve(other)
  }

  override fun resolveSibling(other: Path): Path {
    return path.resolveSibling(other)
  }

  override fun resolveSibling(other: String): Path {
    return path.resolveSibling(other)
  }

  override fun toFile(): File {
    return path.toFile()
  }

  override fun register(
    watcher: WatchService,
    vararg events: WatchEvent.Kind<*>,
  ): WatchKey {
    return path.register(watcher, *events)
  }

  override fun iterator(): MutableIterator<Path> {
    return path.iterator()
  }

  override fun forEach(action: Consumer<in Path>?) {
    path.forEach(action)
  }

  override fun spliterator(): Spliterator<Path?> {
    return path.spliterator()
  }
}

fun ZipFile.getContent(entryName: String): String {
  return getStream(entryName).bufferedReader().use { it.readText() }
}

fun ZipFile.getStream(entryName: String): InputStream {
  val entry = getEntry(entryName) ?: error("Entry $entryName not found in all entries: ${entries().toList()}")
  return getInputStream(entry)
}

fun Assert<JarPath>.getContent(entryName: String) = transform { it.getContent(entryName) }

fun Assert<JarPath>.getMainAttr(name: String) = transform { it.getMainAttr(name) }

fun Assert<JarPath>.containsEntries(vararg entries: String) = given { actual ->
  entries.forEach { entry ->
    actual.getEntry(entry)
      ?: fail("Jar file ${actual.path} does not contain entry $entry in entries: ${actual.entries().toList()}")
  }
}

fun Assert<JarPath>.doesNotContainEntries(vararg entries: String) = given { actual ->
  entries.forEach { entry ->
    actual.getEntry(entry) ?: return@forEach
    fail("Jar file ${actual.path} contains entry $entry in entries: ${actual.entries().toList()}")
  }
}
