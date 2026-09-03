package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import java.io.File
import java.nio.charset.Charset
import org.gradle.api.tasks.bundling.ZipEntryCompression

internal fun generateShadowedSourcesJar(
  sourcesJarFile: File,
  sourceSetsSourceDirs: Iterable<File>,
  includedSourcesJars: Iterable<File>,
  relocators: Iterable<Relocator>,
  unusedClasses: Set<String> = emptySet(),
  entryCompression: ZipEntryCompression,
  isZip64: Boolean,
  metadataCharset: String?,
  preserveFileTimestamps: Boolean,
) {
  val sourcesJars = includedSourcesJars.filter { it.exists() && it.isFile }
  if (sourceSetsSourceDirs.none() && sourcesJars.isEmpty()) return

  val visitedFiles = mutableSetOf<String>()
  val charset = metadataCharset?.let(Charset::forName) ?: Charsets.UTF_8

  try {
    sourcesJarFile
      .createZipOutputStream(
        entryCompression = entryCompression,
        isZip64 = isZip64,
        encoding = metadataCharset,
      )
      .use { zos ->
        val manifestEntry = "META-INF/MANIFEST.MF"
        visitedFiles.add(manifestEntry)
        zos.writeEntry(
          name = manifestEntry,
          preserveLastModified = preserveFileTimestamps,
          unixMode = UnixMode.file(),
        ) {
          write("Manifest-Version: 1.0\n\n".toByteArray(charset))
        }

        for (srcDir in sourceSetsSourceDirs) {
          if (!srcDir.exists()) continue
          srcDir
            .walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
              val relPath = file.relativeTo(srcDir).invariantSeparatorsPath
              val isSource = isSourceFile(relPath)
              if (isSource) {
                val text = file.readText(charset)
                val pkg = extractPackage(text)
                val simpleName = file.name
                if (isUnused(simpleName, pkg, text, unusedClasses)) return@forEach
                val canonicalPath =
                  if (pkg.isEmpty()) simpleName else "${pkg.replace('.', '/')}/$simpleName"
                val relocatedPath = relocators.relocatePath(canonicalPath)
                if (visitedFiles.add(relocatedPath)) {
                  var transformedText = text
                  for (relocator in relocators) {
                    transformedText = relocator.applyToSourceContent(transformedText)
                  }
                  val bytes = transformedText.toByteArray(charset)
                  zos.writeEntry(
                    name = relocatedPath,
                    preserveLastModified = preserveFileTimestamps,
                    lastModified = file.lastModified(),
                    unixMode = UnixMode.file(),
                  ) {
                    write(bytes)
                  }
                }
              } else {
                val relocatedPath = relocators.relocatePath(relPath)
                if (visitedFiles.add(relocatedPath)) {
                  val bytes = file.readBytes()
                  zos.writeEntry(
                    name = relocatedPath,
                    preserveLastModified = preserveFileTimestamps,
                    lastModified = file.lastModified(),
                    unixMode = UnixMode.file(),
                  ) {
                    write(bytes)
                  }
                }
              }
            }
        }

        sourcesJars.forEach { jarFile ->
          jarFile.useZip {
            entries().toList().forEach { entry ->
              if (entry.isDirectory) return@forEach
              val name = entry.name
              if (
                name == "META-INF/MANIFEST.MF" ||
                  name.endsWith(".class") ||
                  name.startsWith("META-INF/INDEX.LIST") ||
                  (name.startsWith("META-INF/") &&
                    (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA")))
              ) {
                return@forEach
              }
              val isSource = isSourceFile(name)
              if (isSource) {
                val text = getInputStream(entry).bufferedReader(charset).readText()
                val pkg = extractPackage(text)
                val simpleName = name.substringAfterLast('/')
                if (isUnused(simpleName, pkg, text, unusedClasses)) return@forEach
                val canonicalPath =
                  if (pkg.isEmpty()) simpleName else "${pkg.replace('.', '/')}/$simpleName"
                val relocatedPath = relocators.relocatePath(canonicalPath)
                if (visitedFiles.add(relocatedPath)) {
                  var transformedText = text
                  for (relocator in relocators) {
                    transformedText = relocator.applyToSourceContent(transformedText)
                  }
                  val bytes = transformedText.toByteArray(charset)
                  zos.writeEntry(
                    name = relocatedPath,
                    preserveLastModified = preserveFileTimestamps,
                    lastModified = entry.time,
                    unixMode = UnixMode.file(),
                  ) {
                    write(bytes)
                  }
                }
              } else {
                val relocatedPath = relocators.relocatePath(name)
                if (visitedFiles.add(relocatedPath)) {
                  val bytes = getInputStream(entry).readBytes()
                  zos.writeEntry(
                    name = relocatedPath,
                    preserveLastModified = preserveFileTimestamps,
                    lastModified = entry.time,
                    unixMode = UnixMode.file(),
                  ) {
                    write(bytes)
                  }
                }
              }
            }
          }
        }

        val entries = zos.entries.map { it.name }
        val added = entries.toMutableSet()
        entries.forEach { name ->
          name.parentDirectoryEntries().asReversed().forEach { entryName ->
            if (!added.add(entryName)) return@forEach
            zos.writeEntry(
              name = entryName,
              preserveLastModified = preserveFileTimestamps,
              unixMode = UnixMode.directory(),
            )
          }
        }
      }
  } catch (e: Exception) {
    sourcesJarFile.delete()
    throw e
  }
}

private val packageRegex = """(?:^|\n)\s*package\s+([a-zA-Z0-9_.]+)""".toRegex()

private val jvmNameRegex =
  """@file\s*:\s*(?:\[[^]]*\b)?(?:kotlin\s*\.\s*jvm\s*\.\s*)?JvmName\s*\(\s*(?:name\s*=\s*)?"([^"]+)""""
    .toRegex()

internal fun extractPackage(text: String): String {
  val matches = packageRegex.findAll(text).map { it.groupValues[1] }.toList()
  return if (matches.isEmpty()) "" else matches.joinToString(".")
}

internal fun isUnused(
  fileName: String,
  pkg: String,
  text: String,
  unusedClasses: Set<String>,
): Boolean {
  if (unusedClasses.isEmpty()) return false
  val simpleName = fileName.substringBeforeLast('.')
  val className = if (pkg.isEmpty()) simpleName else "$pkg.$simpleName"
  if (unusedClasses.contains(className)) return true

  if (fileName.endsWith(".kt")) {
    val customJvmName = jvmNameRegex.find(text)?.groupValues?.get(1)
    val facadeName = customJvmName ?: "${simpleName}Kt"
    val facadeClassName = if (pkg.isEmpty()) facadeName else "$pkg.$facadeName"
    if (unusedClasses.contains(facadeClassName)) return true
  }

  return false
}

private fun isSourceFile(path: String): Boolean {
  return path.endsWith(".java") ||
    path.endsWith(".kt") ||
    path.endsWith(".groovy") ||
    path.endsWith(".scala")
}
