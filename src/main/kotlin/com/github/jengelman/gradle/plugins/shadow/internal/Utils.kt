package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.Properties
import java.util.jar.Attributes.Name as JarAttributeName
import kotlin.io.path.inputStream
import kotlin.io.path.toPath
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFileTreeElement
import org.gradle.internal.file.Chmod
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.Stat

/**
 * Known as `Main-Class` in the manifest file.
 */
internal val mainClassAttributeKey = JarAttributeName.MAIN_CLASS.toString()

/**
 * Known as `Class-Path` in the manifest file.
 */
internal val classPathAttributeKey = JarAttributeName.CLASS_PATH.toString()

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

private val DummyFile = File("dummy")
private val DummyChmod = Chmod { _, _ -> error("This is a dummy implementation.") }
private val DummyStat = object : Stat {
  override fun getUnixMode(f: File): Int = error("This is a dummy implementation.")
  override fun stat(f: File): FileMetadata = error("This is a dummy implementation.")
}

private object Utils
