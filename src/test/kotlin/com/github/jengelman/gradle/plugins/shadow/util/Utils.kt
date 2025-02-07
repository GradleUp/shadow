package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import java.io.OutputStream
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder

val testObjectFactory: ObjectFactory = ProjectBuilder.builder().build().objects

@Suppress("TestFunctionName")
fun SimpleRelocator(
  pattern: String? = null,
  shadedPattern: String? = null,
  includes: List<String> = emptyList(),
  excludes: List<String> = emptyList(),
  rawString: Boolean = false,
): SimpleRelocator = SimpleRelocator(
  objectFactory = testObjectFactory,
  pattern = pattern,
  shadedPattern = shadedPattern,
  includes = includes,
  excludes = excludes,
  rawString = rawString,
)

fun OutputStream.zipOutputStream(): ZipOutputStream {
  return if (this is ZipOutputStream) this else ZipOutputStream(this)
}
