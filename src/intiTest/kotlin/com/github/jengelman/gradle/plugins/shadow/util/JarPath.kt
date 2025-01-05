package com.github.jengelman.gradle.plugins.shadow.util

import assertk.Assert
import assertk.fail
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.deleteExisting

/**
 * A wrapper for [JarFile] that also implements [Path].
 *
 * We must declare some functions like [kotlin.io.path.deleteExisting] explicitly as they could not
 * be delegated to [JarPath] type.
 */
class JarPath(val path: Path) :
  JarFile(path.toFile()),
  Path by path {

  fun deleteExisting() {
    close()
    path.deleteExisting()
  }

  fun getEntryContent(entryName: String): String {
    val entry = getEntry(entryName) ?: error("Entry not found: $entryName")
    return getInputStream(entry).bufferedReader().readText()
  }
}

fun Assert<JarPath>.containsEntries(entries: Iterable<String>) = transform {
  entries.forEach { entry ->
    it.getEntry(entry) ?: fail("Jar file ${it.path} does not contain entry $entry")
  }
}

fun Assert<JarPath>.doesNotContainEntries(entries: Iterable<String>) = transform {
  entries.forEach { entry ->
    it.getEntry(entry) ?: return@forEach
    fail("Jar file ${it.path} contains entry $entry")
  }
}
