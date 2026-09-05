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

        val sourceItems = sourceSetsSourceDirs.filter { it.exists() }
        val (dirs, files) = sourceItems.partition { it.isDirectory }
        val normalizedDirs =
          dirs.map { it to it.normalize().toPath() }.sortedByDescending { it.second.nameCount }

        val filesWithRelPaths = mutableListOf<Pair<File, String>>()
        val coveredDirs = mutableSetOf<File>()

        for (file in files.sortedBy { it.path }) {
          val filePath = file.normalize().toPath()
          val matchingDir =
            normalizedDirs.firstOrNull { (_, dirPath) -> filePath.startsWith(dirPath) }?.first
          if (matchingDir != null) {
            coveredDirs.add(matchingDir)
            filesWithRelPaths.add(file to file.relativeTo(matchingDir).invariantSeparatorsPath)
          } else {
            filesWithRelPaths.add(file to file.name)
          }
        }

        for ((dir, dirPath) in normalizedDirs.sortedBy { it.second.nameCount }) {
          if (coveredDirs.none { dirPath.startsWith(it.normalize().toPath()) }) {
            coveredDirs.add(dir)
            dir
              .walkTopDown()
              .filter { it.isFile }
              .toList()
              .sortedBy { it.relativeTo(dir).invariantSeparatorsPath }
              .forEach { f ->
                filesWithRelPaths.add(f to f.relativeTo(dir).invariantSeparatorsPath)
              }
          }
        }

        for ((file, relPath) in filesWithRelPaths) {
          val isSource = isSourceFile(relPath)
          if (isSource) {
            val text = file.readText(charset)
            val pkg = extractPackage(text)
            val simpleName = file.name
            val canonicalPath =
              if (pkg.isEmpty()) simpleName else "${pkg.replace('.', '/')}/$simpleName"
            if (isUnused(canonicalPath, unusedClasses, sourceToClasses)) continue
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
                  val text = getInputStream(entry).bufferedReader(charset).readText()
                  val pkg = extractPackage(text)
                  val simpleName = name.substringAfterLast('/')
                  val canonicalPath =
                    if (pkg.isEmpty()) simpleName else "${pkg.replace('.', '/')}/$simpleName"
                  if (isUnused(canonicalPath, unusedClasses, sourceToClasses)) return@forEach
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

private val packageRegex = """(?:^|\n)\s*package\s+([a-zA-Z0-9_.]+)""".toRegex()

internal fun extractPackage(text: String): String {
  val matches = packageRegex.findAll(text).map { it.groupValues[1] }.toList()
  return if (matches.isEmpty()) "" else matches.joinToString(".")
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
      val reader = org.vafer.jdeb.shaded.objectweb.asm.ClassReader(bytes)
      reader.accept(
        object :
          org.vafer.jdeb.shaded.objectweb.asm.ClassVisitor(
            org.vafer.jdeb.shaded.objectweb.asm.Opcodes.ASM9
          ) {
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
        org.vafer.jdeb.shaded.objectweb.asm.ClassReader.SKIP_CODE or
          org.vafer.jdeb.shaded.objectweb.asm.ClassReader.SKIP_FRAMES,
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

  for (dir in classesDirs.filter { it.isDirectory }) {
    dir
      .walkTopDown()
      .filter { it.isFile && it.name.endsWith(".class") }
      .forEach { file -> processClassBytes(file.readBytes()) }
  }

  for (file in
    dependencies.filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".zip")) }) {
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
  if (classes.isEmpty()) return false
  return classes.all { it in unusedClasses }
}

private fun isSourceFile(path: String): Boolean {
  return path.endsWith(".java") ||
    path.endsWith(".kt") ||
    path.endsWith(".groovy") ||
    path.endsWith(".scala")
}
