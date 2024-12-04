package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.internal.file.Chmod
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.Stat

/**
 * Return `runtimeClasspath` or `runtime` configuration.
 */
internal inline val Project.runtimeConfiguration: Configuration get() {
  return configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
    ?: configurations.getByName("runtime")
}

internal inline fun <reified T : Any> ObjectFactory.property(defaultValue: T? = null): Property<T> {
  return property(T::class.java).apply {
    if (defaultValue != null) convention(defaultValue)
  }
}

/**
 * This is used for creating a [DefaultFileTreeElement] with default values.
 * [file], [chmod], and [stat] should be non-null, so they are set to dummy values here.
 */
internal fun createDefaultFileTreeElement(
  file: File = DummyFile,
  relativePath: RelativePath,
  chmod: Chmod = DummyChmod,
  stat: Stat = DummyStat,
): DefaultFileTreeElement {
  return DefaultFileTreeElement(file, relativePath, chmod, stat)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T> unsafeLazy(noinline initializer: () -> T): Lazy<T> {
  return lazy(LazyThreadSafetyMode.NONE, initializer)
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

internal fun Class<*>.requireResourceAsText(name: String): String {
  return requireResourceAsStream(name).bufferedReader().readText()
}

private fun Class<*>.requireResourceAsStream(name: String): InputStream {
  return getResourceAsStream(name) ?: throw FileNotFoundException("Resource $name not found.")
}

private val DummyFile = File("dummy")
private val DummyChmod = Chmod { _, _ -> error("This is a dummy implementation.") }
private val DummyStat = object : Stat {
  override fun getUnixMode(f: File): Int = error("This is a dummy implementation.")
  override fun stat(f: File): FileMetadata = error("This is a dummy implementation.")
}
