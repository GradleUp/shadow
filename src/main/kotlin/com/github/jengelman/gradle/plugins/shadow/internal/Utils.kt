package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Properties
import java.util.jar.Attributes.Name as JarAttributeName
import org.apache.tools.zip.ZipEntry

/** Known as `Main-Class` in the manifest file. */
internal val mainClassAttributeKey = JarAttributeName.MAIN_CLASS.toString()

/** Known as `Class-Path` in the manifest file. */
internal val classPathAttributeKey = JarAttributeName.CLASS_PATH.toString()

/** Known as `Multi-Release` in the manifest file. */
internal val multiReleaseAttributeKey = JarAttributeName.MULTI_RELEASE.toString()

/**
 * Unsafe cast, copied from
 * https://github.com/JetBrains/kotlin/blob/d3200b2c65b829b85244c4ec4cb19f6e479b06ba/core/util.runtime/src/org/jetbrains/kotlin/utils/addToStdlib.kt#L111
 */
internal inline fun <reified T : Any> Any?.cast(): T = this as T

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T : Any> unsafeLazy(noinline initializer: () -> T): Lazy<T> =
  lazy(LazyThreadSafetyMode.NONE, initializer)

internal inline fun zipEntry(
  name: String,
  preserveLastModified: Boolean = true,
  lastModified: Long = -1,
  block: ZipEntry.() -> Unit = {},
): ZipEntry =
  ZipEntry(name).apply {
    if (preserveLastModified) {
      if (lastModified >= 0) {
        time = lastModified
      }
    } else {
      time = ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    }
    block()
  }

internal fun Properties.inputStream(
  charset: Charset = Charsets.ISO_8859_1,
  comments: String = "",
): ByteArrayInputStream {
  val os = ByteArrayOutputStream()
  os.writer(charset).use { writer -> store(writer, comments) }
  return os.toByteArray().inputStream()
}
