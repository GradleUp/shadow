package com.github.jengelman.gradle.plugins.shadow.testkit

import assertk.Assert
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsNone
import assertk.assertions.containsOnly
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.JarInputStream
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

  override fun toString(): String = path.toString()
}

fun ZipFile.getBytes(entryName: String): ByteArray {
  return getStream(entryName).use { it.readBytes() }
}

fun ZipFile.getContent(entryName: String): String {
  return getStream(entryName).bufferedReader().use { it.readText() }
}

fun ZipFile.getStream(entryName: String): InputStream {
  val entry = checkNotNull(getEntry(entryName)) { "Entry $entryName not found in all entries: ${entries().toList()}" }
  return getInputStream(entry)
}

fun Assert<JarPath>.getContent(entryName: String) = transform { it.getContent(entryName) }

/**
 * Scans the jar file for all entries that match the specified [entryName],
 * [getContent] or [getStream] return only one of the matching entries;
 * which one these functions return is undefined.
 */
fun Assert<JarPath>.getContents(entryName: String) = transform {
  Files.newInputStream(it.path).use {
    val jarInput = JarInputStream(it)
    val contents = mutableListOf<String>()
    while (true) {
      val entry = jarInput.nextEntry ?: break
      if (entry.name == entryName) {
        contents.add(jarInput.readAllBytes().toString(Charsets.UTF_8))
      }
      jarInput.closeEntry()
    }
    contents
  }
}

fun Assert<JarPath>.getMainAttr(name: String) = transform { it.getMainAttr(name) }

/**
 * Ensures the JAR contains at least the specified entries.
 * Commonly used with [containsNone] to verify additional constraints.
 */
fun Assert<JarPath>.containsAtLeast(vararg entries: String) = toEntries().containsAtLeast(*entries)

/**
 * Ensures the JAR does not contain any of the specified entries.
 * Commonly used with [containsAtLeast] for stricter checks.
 */
fun Assert<JarPath>.containsNone(vararg entries: String) = toEntries().containsNone(*entries)

/**
 * Ensures the JAR contains only the specified entries.
 * Used alone, without [containsAtLeast] or [containsNone].
 */
fun Assert<JarPath>.containsOnly(vararg entries: String) = toEntries().containsOnly(*entries)

/**
 * Ensures the JAR contains only the specified entries.
 * Used alone, without [containsAtLeast] or [containsNone].
 */
fun Assert<JarPath>.containsExactlyInAnyOrder(vararg entries: String) = toEntries().containsExactlyInAnyOrder(*entries)

private fun Assert<JarPath>.toEntries() = transform { actual ->
  actual.entries().toList().map { it.name }
}
