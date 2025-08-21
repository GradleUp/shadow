/*
 * This file is part of KSR, a gradle plugin for handling Kotlin metadata relocation.
 *
 * MIT License
 *
 * Copyright (c) 2025 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.ksr

import com.github.jengelman.gradle.plugins.shadow.internal.RelocationClassWriter
import com.github.jengelman.gradle.plugins.shadow.internal.RelocationClassWriter.Companion.applyClassRelocation
import com.github.jengelman.gradle.plugins.shadow.relocation.CacheableRelocator
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.metadata.jvm.JvmMetadataVersion
import kotlin.metadata.jvm.KmModule
import kotlin.metadata.jvm.KmPackageParts
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi
import org.gradle.api.Action
import org.objectweb.asm.ClassReader

/**
 * A wrapper around [ShadowJar.relocate] that will also take care of kotlin metadata. Only use it
 * for kotlin libraries. Using it on normal JVM libraries will just increase compilation time.
 */
internal fun ShadowJar.kotlinRelocate(
  pattern: String,
  shadedPattern: String,
  action: Action<SimpleRelocator> = Action { },
) {
  val relocator = KotlinRelocator(pattern, shadedPattern)
  val intersections = relocators.get()
    .filterIsInstance<KotlinRelocator>()
    .filter { it.canRelocatePath(pattern) }
  require(intersections.isEmpty()) {
    "Can't relocate from $pattern to $shadedPattern as it clashes with another paths: ${intersections.joinToString()}"
  }
  relocate(relocator, action)
}

internal fun relocateMetadata(task: ShadowJar) {
  val relocators = task.relocators.get().filterIsInstance<KotlinRelocator>()
  val zip = task.archiveFile.get().asFile.toPath()
  FileSystems.newFileSystem(zip, null as ClassLoader?).use { fs ->
    Files.walk(fs.getPath("/")).forEach { path ->
      if (!Files.isRegularFile(path)) return@forEach
      if (path.name.endsWith(".class")) relocateClass(path, relocators)
      if (path.name.endsWith(".kotlin_module")) relocateKotlinModule(path, relocators)
    }
  }
}

internal fun Iterable<KotlinRelocator>.applyPathRelocation(value: String): String = fold(value) { string, relocator -> relocator.relocatePath(RelocatePathContext(string)) }

@CacheableRelocator
internal class KotlinRelocator(pattern: String, shadedPattern: String) : SimpleRelocator(pattern, shadedPattern, emptyList(), emptyList()) {
  // I hate these hacks...
  private val shadedPattern = shadedPattern.replace('/', '.')
  private val shadedPathPattern = shadedPattern.replace('.', '/')
  private val pattern = pattern.replace('/', '.')
  private val pathPattern = pattern.replace('.', '/')

  // Replace all instead of first
  override fun relocateClass(context: RelocateClassContext): String = context.className.replace(pattern.toRegex(), shadedPattern)

  // Replace all instead of first
  override fun relocatePath(context: RelocatePathContext): String = context.path.replace(pathPattern.toRegex(), shadedPathPattern)
}

private fun relocateClass(file: Path, relocators: List<KotlinRelocator>) {
  Files.newInputStream(file).use { ins ->
    val cr = ClassReader(ins)
    val cw = RelocationClassWriter(cr, relocators.toSet())
    val scanner = MetadataAnnotationScanner(cw, relocators)
    cr.accept(scanner, 0)
    if (scanner.isRelocated || cw.isRelocated) {
      ins.close()
      Files.delete(file)
      Files.write(file, cw.toByteArray())
    }
  }
}

@OptIn(UnstableMetadataApi::class)
private fun relocateKotlinModule(file: Path, relocators: List<KotlinRelocator>) {
  Files.newInputStream(file).use { ins ->
    val metadata = KotlinModuleMetadata.read(ins.readBytes())
    val result = KmModule()
    for ((pkg, parts) in metadata.kmModule.packageParts) {
      result.packageParts[relocators.applyPathRelocation(pkg)] =
        KmPackageParts(
          parts.fileFacades.mapTo(mutableListOf(), relocators::applyPathRelocation),
          parts.multiFileClassParts.entries.associateTo(mutableMapOf()) { (name, facade) ->
            relocators.applyClassRelocation(name) to
              relocators.applyPathRelocation(facade)
          },
        )
    }
    ins.close()
    Files.delete(file)
    Files.write(
      file,
      KotlinModuleMetadata(result, JvmMetadataVersion.LATEST_STABLE_SUPPORTED).write(),
    )
  }
}
