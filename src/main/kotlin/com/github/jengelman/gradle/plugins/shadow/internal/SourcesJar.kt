package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import java.io.File
import java.nio.charset.Charset
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.vafer.jdeb.shaded.objectweb.asm.ClassReader
import org.vafer.jdeb.shaded.objectweb.asm.ClassVisitor
import org.vafer.jdeb.shaded.objectweb.asm.Opcodes

internal fun generateSourcesJar(
  sourcesJarFile: File,
  sourceSetsSourceDirs: FileCollection,
  includedSourcesJars: Iterable<File>,
  classesDirs: Iterable<File> = emptyList(),
  dependencies: Iterable<File> = emptyList(),
  relocators: Iterable<Relocator>,
  unusedClasses: Set<String> = emptySet(),
  entryCompression: ZipEntryCompression,
  isZip64: Boolean,
  metadataCharset: String?,
  preserveFileTimestamps: Boolean,
) {
  val sourcesJars = includedSourcesJars.filter { it.exists() && it.isFile }.sortedBy { it.path }

  val visitedFiles = mutableSetOf<String>()
  val charset = metadataCharset?.let(Charset::forName) ?: Charsets.UTF_8
  val sourceToClasses =
    if (unusedClasses.isNotEmpty()) {
      buildSourceToClassesMap(classesDirs = classesDirs, dependencies = dependencies)
    } else {
      emptyMap()
    }

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

        val filesWithRelPaths = mutableListOf<Pair<File, String>>()
        sourceSetsSourceDirs.asFileTree.visit { details ->
          if (!details.isDirectory) {
            filesWithRelPaths.add(details.file to details.relativePath.pathString)
          }
        }

        for ((file, relPath) in filesWithRelPaths.sortedBy { it.second }) {
          val isSource = isSourceFile(relPath)
          if (isSource) {
            if (isUnused(relPath, unusedClasses, sourceToClasses)) continue
            val relocatedPath = relocators.relocateSourcePath(relPath)
            if (visitedFiles.add(relocatedPath)) {
              val text = file.readText(charset)
              val transformedText = relocators.remapSource(text)
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

        sourcesJars.forEach { jarFile ->
          jarFile.useZip {
            entries()
              .toList()
              .filterNot { it.isDirectory }
              .sortedBy { it.name }
              .forEach { entry ->
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
                  if (isUnused(name, unusedClasses, sourceToClasses)) return@forEach
                  val relocatedPath = relocators.relocateSourcePath(name)
                  if (visitedFiles.add(relocatedPath)) {
                    val text = getInputStream(entry).bufferedReader(charset).readText()
                    val transformedText = relocators.remapSource(text)
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
          name.parentDirectoryEntries().forEach { entryName ->
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
    gradleError("Could not create shadowed sources JAR '$sourcesJarFile'.", e)
  }
}

internal fun buildSourceToClassesMap(
  classesDirs: Iterable<File>,
  dependencies: Iterable<File>,
): Map<String, Set<String>> {
  val sourceToClasses = mutableMapOf<String, MutableSet<String>>()

  fun processClassBytes(bytes: ByteArray) {
    try {
      var internalName: String? = null
      var sourceFile: String? = null
      ClassReader(bytes)
        .accept(
          object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(
              version: Int,
              access: Int,
              name: String,
              signature: String?,
              superName: String?,
              interfaces: Array<out String>?,
            ) {
              internalName = name
              super.visit(version, access, name, signature, superName, interfaces)
            }

            override fun visitSource(source: String?, debug: String?) {
              sourceFile = source
              super.visitSource(source, debug)
            }
          },
          ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
        )

      val name = internalName ?: return
      val source = sourceFile ?: return
      val pkg = name.substringBeforeLast('/', "")
      val canonicalSourcePath = if (pkg.isEmpty()) source else "$pkg/$source"
      val className = name.replace('/', '.')
      sourceToClasses.getOrPut(canonicalSourcePath) { mutableSetOf() }.add(className)
    } catch (_: Exception) {
      // Ignore invalid class files
    }
  }

  for (dir in classesDirs.filter(File::isDirectory)) {
    dir
      .walkTopDown()
      .filter { it.isFile && it.name.endsWith(".class") }
      .forEach { file -> processClassBytes(file.readBytes()) }
  }

  for (file in
    dependencies.filter { it.isFile && (it.extension == "jar" || it.extension == "zip") }) {
    try {
      file.useZip {
        entries()
          .toList()
          .filter { !it.isDirectory && it.name.endsWith(".class") }
          .forEach { entry -> processClassBytes(getInputStream(entry).readBytes()) }
      }
    } catch (_: Exception) {
      // Ignore invalid archives
    }
  }

  return sourceToClasses
}

internal fun isUnused(
  canonicalPath: String,
  unusedClasses: Set<String>,
  sourceToClasses: Map<String, Set<String>>,
): Boolean {
  if (unusedClasses.isEmpty()) return false
  val classes = sourceToClasses[canonicalPath] ?: return false
  return classes.isNotEmpty() && classes.all { it in unusedClasses }
}
