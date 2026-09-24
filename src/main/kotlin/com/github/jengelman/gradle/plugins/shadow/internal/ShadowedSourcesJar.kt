package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import java.io.File
import java.io.InputStream
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileCollection
import org.vafer.jdeb.shaded.objectweb.asm.ClassReader
import org.vafer.jdeb.shaded.objectweb.asm.ClassVisitor
import org.vafer.jdeb.shaded.objectweb.asm.Opcodes

internal fun generateSourcesJar(
  sourcesJarFile: File,
  zipOutStream: ZipOutputStream,
  sourceSetsSourceDirs: FileCollection,
  includedSourcesJars: Iterable<File>,
  classesDirs: Iterable<File>,
  dependencies: Iterable<File>,
  relocators: Iterable<Relocator>,
  unusedClasses: Set<String>,
  preserveFileTimestamps: Boolean,
) =
  try {
    zipOutStream.use { zos ->
      val sourcesJars = includedSourcesJars.filter { it.exists() && it.isFile }.sortedBy { it.path }

      val visitedFiles = mutableSetOf<String>()
      val sourceToClasses =
        if (unusedClasses.isNotEmpty()) {
          buildSourceToClassesMap(classesDirs = classesDirs, dependencies = dependencies)
        } else {
          emptyMap()
        }

      val manifestEntry = "META-INF/MANIFEST.MF"
      visitedFiles.add(manifestEntry)
      zos.writeEntry(
        name = manifestEntry,
        preserveLastModified = preserveFileTimestamps,
        unixMode = UnixMode.file(),
      ) {
        write("Manifest-Version: 1.0\n\n".toByteArray())
      }

      val filesWithRelPaths = mutableListOf<Pair<File, String>>()
      sourceSetsSourceDirs.asFileTree.visit { details ->
        if (!details.isDirectory) {
          filesWithRelPaths.add(details.file to details.relativePath.pathString)
        }
      }

      // Relocates and writes a project or dependency source file, or a resource.
      fun addEntry(name: String, lastModified: Long, readBytes: () -> ByteArray) {
        if (name.isModuleInfo) return
        val isSource = name.isSourceFile()
        if (isSource && isUnused(name, unusedClasses, sourceToClasses)) return
        val relocatedPath =
          if (isSource) relocators.relocateSourcePath(name) else relocators.relocatePath(name)
        if (!visitedFiles.add(relocatedPath)) return
        val bytes =
          if (isSource) {
            relocators.remapSource(readBytes().decodeToString()).toByteArray()
          } else {
            readBytes()
          }
        zos.writeEntry(
          name = relocatedPath,
          preserveLastModified = preserveFileTimestamps,
          lastModified = lastModified,
          unixMode = UnixMode.file(),
        ) {
          write(bytes)
        }
      }

      filesWithRelPaths
        .sortedBy { it.second }
        .forEach { (file, relPath) ->
          addEntry(relPath, file.lastModified()) { file.readBytes() }
        }

      sourcesJars.forEach { jarFile ->
        jarFile.useZip {
          entries()
            .toList()
            .filterNot { entry ->
              val name = entry.name
              entry.isDirectory ||
                name == "META-INF/MANIFEST.MF" ||
                name.endsWith(".class") ||
                name.startsWith("META-INF/INDEX.LIST") ||
                (name.startsWith("META-INF/") &&
                  (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA")))
            }
            .sortedBy { it.name }
            .forEach { entry ->
              addEntry(entry.name, entry.time) { getInputStream(entry).use { it.readBytes() } }
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

internal fun isUnused(
  canonicalPath: String,
  unusedClasses: Set<String>,
  sourceToClasses: Map<String, Set<String>>,
): Boolean {
  if (unusedClasses.isEmpty()) return false
  val classes = sourceToClasses[canonicalPath] ?: return false
  return classes.isNotEmpty() && classes.all { it in unusedClasses }
}

private fun buildSourceToClassesMap(
  classesDirs: Iterable<File>,
  dependencies: Iterable<File>,
): Map<String, Set<String>> {
  val sourceToClasses = mutableMapOf<String, MutableSet<String>>()

  fun InputStream.recordSourceMapping() = use {
    try {
      var internalName: String? = null
      var sourceFile: String? = null
      ClassReader(this)
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

      val name = internalName ?: return@use
      val source = sourceFile ?: return@use
      val pkg = name.substringBeforeLast('/', "")
      val canonicalSourcePath = if (pkg.isEmpty()) source else "$pkg/$source"
      val className = name.replace('/', '.')
      sourceToClasses.getOrPut(canonicalSourcePath) { mutableSetOf() }.add(className)
    } catch (_: Exception) {
      // Ignore invalid class files.
    }
  }

  classesDirs
    .filter(File::isDirectory)
    .flatMap(File::walk)
    .filter { it.isFile && it.name.endsWith(".class") }
    .forEach { file -> file.inputStream().recordSourceMapping() }

  dependencies
    .filter(File::isFile)
    .filter {
      it.extension.equals("jar", ignoreCase = true) || it.extension.equals("zip", ignoreCase = true)
    }
    .forEach { file ->
      try {
        file.useZip {
          entries()
            .toList()
            .filter { it.name.endsWith(".class") }
            .forEach { entry -> getInputStream(entry).recordSourceMapping() }
        }
      } catch (_: Exception) {
        // Ignore invalid archives.
      }
    }

  return sourceToClasses
}

// Aligned with the default exclusion of `module-info.class` from the shadowed JAR.
private val String.isModuleInfo: Boolean
  get() = substringAfterLast('/') == "module-info.java"
