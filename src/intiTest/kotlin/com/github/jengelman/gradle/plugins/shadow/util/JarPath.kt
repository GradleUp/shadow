package com.github.jengelman.gradle.plugins.shadow.util

import assertk.Assert
import assertk.all
import assertk.assertions.isNotEmpty
import assertk.fail
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * A wrapper for [JarFile] that also implements [Path].
 *
 * We must declare some functions like [kotlin.io.path.deleteExisting] explicitly as they could not
 * be delegated to [JarPath] type.
 */
class JarPath private constructor(val path: Path) :
  JarFile(path.toFile()),
  Path by path {

  fun deleteExisting() {
    close()
    path.deleteExisting()
  }

  override fun close() {
    super.close()
    cached.remove(path)
  }

  fun getContent(entryName: String): String {
    val entry = getEntry(entryName) ?: error("Entry not found: $entryName")
    return getInputStream(entry).bufferedReader().readText()
  }

  companion object {
    private val cached = ConcurrentHashMap<Path, JarPath>()

    fun from(path: Path): JarPath {
      check(path.exists()) { "Path not found: $path" }
      check(path.isRegularFile()) { "Path is not a regular file: $path" }

      return cached[path] ?: JarPath(path).also { cached[path] = it }
    }
  }
}

fun Assert<JarPath>.useAll(body: Assert<JarPath>.() -> Unit) = all {
  body()
  given { it.close() }
}

/**
 * Common regular assertions for [JarPath].
 */
fun Assert<JarPath>.isRegular() = transform { it.entries().toList() }.isNotEmpty()

fun Assert<JarPath>.containsEntries(vararg entries: String) = transform { actual ->
  entries.forEach { entry ->
    actual.getEntry(entry) ?: fail("Jar file ${actual.path} does not contain entry $entry")
  }
}

fun Assert<JarPath>.doesNotContainEntries(vararg entries: String) = transform { actual ->
  entries.forEach { entry ->
    actual.getEntry(entry) ?: return@forEach
    fail("Jar file ${actual.path} contains entry $entry")
  }
}
