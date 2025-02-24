package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.Properties
import java.util.jar.Attributes.Name as JarAttributeName
import kotlin.io.path.toPath
import org.apache.tools.zip.ZipEntry

/**
 * Known as `Main-Class` in the manifest file.
 */
internal val mainClassAttributeKey = JarAttributeName.MAIN_CLASS.toString()

/**
 * Known as `Class-Path` in the manifest file.
 */
internal val classPathAttributeKey = JarAttributeName.CLASS_PATH.toString()

/**
 * Known as `Multi-Release` in the manifest file.
 */
internal val multiReleaseAttributeKey = JarAttributeName.MULTI_RELEASE.toString()

/**
 * Unsafe cast, copied from
 * https://github.com/JetBrains/kotlin/blob/d3200b2c65b829b85244c4ec4cb19f6e479b06ba/core/util.runtime/src/org/jetbrains/kotlin/utils/addToStdlib.kt#L111
 */
internal inline fun <reified T : Any> Any?.cast(): T = this as T

internal inline fun zipEntry(
  name: String,
  preserveLastModified: Boolean = true,
  lastModified: Long = -1,
  block: ZipEntry.() -> Unit = {},
): ZipEntry = ZipEntry(name).apply {
  if (preserveLastModified) {
    if (lastModified >= 0) {
      time = lastModified
    }
  } else {
    time = ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
  }
  block()
}

internal fun Relocator.relocatePath(path: String): String {
  return relocatePath(RelocatePathContext(path))
}

internal fun Relocator.relocateClass(className: String): String {
  return relocateClass(RelocateClassContext(className))
}

internal fun Properties.inputStream(
  charset: Charset = Charsets.ISO_8859_1,
  comments: String = "",
): ByteArrayInputStream {
  val os = ByteArrayOutputStream()
  os.writer(charset).use { writer ->
    store(writer, comments)
  }
  return os.toByteArray().inputStream()
}

internal fun requireResourceAsText(name: String): String {
  return requireResourceAsStream(name).bufferedReader().use { it.readText() }
}

internal fun requireResourceAsStream(name: String): InputStream {
  return Utils::class.java.classLoader.getResourceAsStream(name)
    ?: throw NoSuchFileException("Resource $name not found.")
}

internal fun requireResourceAsPath(name: String): Path {
  val resource = Utils::class.java.classLoader.getResource(name)
    ?: throw NoSuchFileException("Resource $name not found.")
  return resource.toURI().toPath()
}

private object Utils
