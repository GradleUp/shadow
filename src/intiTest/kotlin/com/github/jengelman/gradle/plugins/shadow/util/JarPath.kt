package com.github.jengelman.gradle.plugins.shadow.util

import assertk.Assert
import assertk.assertThat
import assertk.assertions.exists
import assertk.fail
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.deleteExisting

/**
 * A wrapper for [JarFile] that also implements [Path].
 *
 * We must declare some extensions like [kotlin.io.path.deleteExisting] or [assertk.assertions.exists]
 * as they could not be delegated to [JarPath] type.
 */
class JarPath(val path: Path) :
  JarFile(path.toFile()),
  Path by path {

  fun deleteExisting() {
    path.deleteExisting()
  }

  fun getEntryContent(entryName: String): String {
    val entry = getJarEntry(entryName) ?: error("Entry not found: $entryName")
    return getInputStream(entry).bufferedReader().readText()
  }

  fun assertContains(entries: Iterable<String>) {
    entries.forEach { entry ->
      assertThat(getEntry(entry)).given { actual ->
        actual ?: fail("Jar file $path does not contain entry $entry")
      }
    }
  }

  fun assertDoesNotContain(entries: Iterable<String>) {
    entries.forEach { entry ->
      assertThat(getEntry(entry)).given { actual ->
        if (actual == null) return
        fail("Jar file $path contains entry $entry")
      }
    }
  }
}

fun Assert<JarPath>.exists() {
  transform { it.path }.exists()
}
