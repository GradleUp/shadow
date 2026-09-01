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
        for (srcDir in sourceSetsSourceDirs) {
          if (!srcDir.exists()) continue
          srcDir
            .walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
              val relPath = file.relativeTo(srcDir).invariantSeparatorsPath
              if (visitedFiles.add(relPath)) {
                val relocatedPath = relocators.relocatePath(relPath)
                val bytes =
                  if (isSourceFile(relPath)) {
                    var text = file.readText(charset)
                    for (relocator in relocators) {
                      text = relocator.applyToSourceContent(text)
                    }
                    text.toByteArray(charset)
                  } else {
                    file.readBytes()
                  }
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
              val relocatedPath = relocators.relocatePath(name)
              if (visitedFiles.add(relocatedPath)) {
                val bytes =
                  if (isSourceFile(name)) {
                    var text = getInputStream(entry).bufferedReader(charset).readText()
                    for (relocator in relocators) {
                      text = relocator.applyToSourceContent(text)
                    }
                    text.toByteArray(charset)
                  } else {
                    getInputStream(entry).readBytes()
                  }
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

        val entries = zos.entries.map { it.name }
        val added = entries.toMutableSet()
        val currentTimeMillis = System.currentTimeMillis()
        entries.forEach { name ->
          name.parentDirectoryEntries().asReversed().forEach { entryName ->
            if (!added.add(entryName)) return@forEach
            zos.writeEntry(
              name = entryName,
              preserveLastModified = preserveFileTimestamps,
              lastModified = currentTimeMillis,
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

private fun isSourceFile(path: String): Boolean {
  return path.endsWith(".java") ||
    path.endsWith(".kt") ||
    path.endsWith(".groovy") ||
    path.endsWith(".scala")
}
