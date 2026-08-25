package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Properties
import java.util.jar.Attributes.Name as JarAttributeName

/** Known as `Main-Class` in the manifest file. */
internal val mainClassAttributeKey = JarAttributeName.MAIN_CLASS.toString()

/** Known as `Class-Path` in the manifest file. */
internal val classPathAttributeKey = JarAttributeName.CLASS_PATH.toString()

/** Known as `Multi-Release` in the manifest file. */
internal val multiReleaseAttributeKey = JarAttributeName.MULTI_RELEASE.toString()

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T : Any> unsafeLazy(noinline initializer: () -> T): Lazy<T> =
  lazy(LazyThreadSafetyMode.NONE, initializer)

internal fun Properties.inputStream(
  charset: Charset = Charsets.ISO_8859_1,
  comments: String = "",
): ByteArrayInputStream {
  val os = ByteArrayOutputStream()
  os.writer(charset).use { writer -> store(writer, comments) }
  return os.toByteArray().inputStream()
}

@Suppress("NOTHING_TO_INLINE") // Syncs with `appendLine`.
internal inline fun StringBuilder.appendLfLine(value: CharSequence? = null): StringBuilder = apply {
  if (value != null) append(value)
  append('\n')
}
