package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.internal.RelocatorRemapper
import com.github.jengelman.gradle.plugins.shadow.internal.cast
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import java.io.File
import java.util.GregorianCalendar
import java.util.zip.ZipException
import kotlin.metadata.jvm.KmModule
import kotlin.metadata.jvm.KmPackageParts
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper

/**
 * Modified from [org.gradle.api.internal.file.archive.ZipCopyAction.java](https://github.com/gradle/gradle/blob/b893c2b085046677cf858fb3d5ce00e68e556c3a/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/archive/ZipCopyAction.java).
 */
public open class ShadowCopyAction(
  private val zipFile: File,
  private val zosProvider: (File) -> ZipOutputStream,
  private val transformers: Set<ResourceTransformer>,
  private val relocators: Set<Relocator>,
  private val unusedClasses: Set<String>,
  private val preserveFileTimestamps: Boolean,
  private val failOnDuplicateEntries: Boolean,
  private val encoding: String?,
) : CopyAction {
  private val visitedDirs = mutableMapOf<String, FileCopyDetails>()

  override fun execute(stream: CopyActionProcessingStream): WorkResult {
    val zipOutStream = try {
      zosProvider(zipFile)
    } catch (e: Exception) {
      throw GradleException("Could not create ZIP '$zipFile'.", e)
    }

    try {
      zipOutStream.use { zos ->
        stream.process(StreamAction(zos))
        processTransformers(zos)
        addDirs(zos) // This must be called after adding all file entries to avoid duplicate directories being added.
        checkDuplicateEntries(zos)
      }
    } catch (e: Exception) {
      if (e is Zip64RequiredException || e.cause is Zip64RequiredException) {
        val message = if (e is Zip64RequiredException) e.message else e.cause?.message
        throw Zip64RequiredException(
          """
            $message

            To build this archive, please enable the zip64 extension. e.g.
            ```kts
            tasks.shadowJar {
              isZip64 = true
            }
            ```
            See: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Zip.html#org.gradle.api.tasks.bundling.Zip:zip64 for more details.
          """.trimIndent(),
        )
      }
      zipFile.delete()
      throw e
    }
    return WorkResults.didWork(true)
  }

  private fun processTransformers(zos: ZipOutputStream) {
    transformers.forEach { transformer ->
      if (transformer.hasTransformedResource()) {
        transformer.modifyOutputStream(zos, preserveFileTimestamps)
      }
    }
  }

  private fun addDirs(zos: ZipOutputStream) {
    @Suppress("UNCHECKED_CAST")
    val entries = zos.entries.map { it.name }
    val added = entries.toMutableSet()
    val currentTimeMillis = System.currentTimeMillis()

    fun addParent(name: String) {
      val parent = name.substringBeforeLast('/', "")
      val entryName = "$parent/"
      if (parent.isNotEmpty() && added.add(entryName)) {
        val details = visitedDirs[parent]
        val (lastModified, flag) = if (details == null) {
          currentTimeMillis to UnixStat.DEFAULT_DIR_PERM
        } else {
          details.lastModified to details.permissions.toUnixNumeric()
        }
        val entry = zipEntry(entryName, preserveFileTimestamps, lastModified) {
          unixMode = UnixStat.DIR_FLAG or flag
        }
        zos.putNextEntry(entry)
        zos.closeEntry()
        addParent(parent)
      }
    }

    entries.forEach {
      addParent(it)
    }
  }

  private fun checkDuplicateEntries(zos: ZipOutputStream) {
    val entries = zos.entries.map { it.name }
    val duplicates = entries.groupingBy { it }.eachCount().filter { it.value > 1 }
    if (duplicates.isNotEmpty()) {
      val dupEntries = duplicates.entries.joinToString(separator = "\n") {
        "${it.key} (${it.value} times)"
      }
      val message = "Duplicate entries found in the shadowed JAR: \n$dupEntries"
      if (failOnDuplicateEntries) {
        throw GradleException(message)
      } else {
        logger.info(message)
      }
    }
  }

  private inner class StreamAction(
    private val zipOutStr: ZipOutputStream,
  ) : CopyActionProcessingStreamAction {
    init {
      logger.info("Relocator count: ${relocators.size}.")
      if (encoding != null) {
        zipOutStr.setEncoding(encoding)
      }
    }

    override fun processFile(details: FileCopyDetailsInternal) {
      try {
        if (details.isDirectory) {
          visitedDirs[details.path] = details
        } else {
          visitFile(details)
        }
      } catch (e: Exception) {
        throw GradleException("Could not add $details to ZIP '$zipFile'.", e)
      }
    }

    private fun visitFile(fileDetails: FileCopyDetails) {
      val path = fileDetails.path
      when {
        path.endsWith(".class") -> {
          if (isUnused(path)) return
          if (relocators.isEmpty()) {
            fileDetails.writeToZip(path)
          } else {
            fileDetails.remapClass()
          }
        }
        path.endsWith(".kotlin_module") -> {
          if (relocators.isEmpty()) {
            fileDetails.writeToZip(path)
          } else {
            fileDetails.remapKotlinModule()
          }
        }
        else -> {
          val relocated = relocators.relocatePath(path)
          if (transform(fileDetails, relocated)) return
          fileDetails.writeToZip(relocated)
        }
      }
    }

    private fun isUnused(classPath: String): Boolean {
      val className = classPath.substringBeforeLast(".").replace('/', '.')
      return unusedClasses.contains(className).also {
        if (it) {
          logger.info("Dropping unused class: $className")
        }
      }
    }

    /**
     * Applies remapping to the given class with the specified relocation path. The remapped class is then written
     * to the zip file.
     */
    private fun FileCopyDetails.remapClass() = file.readBytes().let { bytes ->
      var modified = false
      val remapper = RelocatorRemapper(relocators) { modified = true }

      // We don't pass the ClassReader here. This forces the ClassWriter to rebuild the constant pool.
      // Copying the original constant pool should be avoided because it would keep references
      // to the original class names. This is not a problem at runtime (because these entries in the
      // constant pool are never used), but confuses some tools such as Felix's maven-bundle-plugin
      // that use the constant pool to determine the dependencies of a class.
      val cw = ClassWriter(0)
      val cr = ClassReader(bytes)
      val cv = ClassRemapper(cw, remapper)

      try {
        cr.accept(cv, ClassReader.EXPAND_FRAMES)
      } catch (t: Throwable) {
        throw GradleException("Error in ASM processing class $path", t)
      }

      val newBytes = if (modified) {
        cw.toByteArray()
      } else {
        // If we didn't need to change anything, keep the original bytes as-is
        bytes
      }

      // Temporarily remove the multi-release prefix.
      val multiReleasePrefix = "^META-INF/versions/\\d+/".toRegex().find(path)?.value.orEmpty()
      val newPath = path.replace(multiReleasePrefix, "")
      val relocatedPath = multiReleasePrefix + relocators.relocatePath(newPath)
      try {
        val entry = zipEntry(relocatedPath, preserveFileTimestamps, lastModified) {
          unixMode = UnixStat.FILE_FLAG or permissions.toUnixNumeric()
        }
        // Now we put it back on so the class file is written out with the right extension.
        zipOutStr.putNextEntry(entry)
        zipOutStr.write(newBytes)
        zipOutStr.closeEntry()
      } catch (_: ZipException) {
        logger.warn("We have a duplicate $relocatedPath in source project")
      }
    }

    /**
     * Applies remapping to the given kotlin module with the specified relocation path.
     * The remapped module is then written to the zip file.
     */
    @OptIn(UnstableMetadataApi::class)
    private fun FileCopyDetails.remapKotlinModule() = file.readBytes().let { bytes ->
      val kmMetadata = KotlinModuleMetadata.read(bytes)
      val newKmModule = KmModule().apply {
        optionalAnnotationClasses += kmMetadata.kmModule.optionalAnnotationClasses
        packageParts += kmMetadata.kmModule.packageParts.map { (pkg, parts) ->
          val relocatedPkg = relocators.relocateClass(pkg)
          val relocatedParts = KmPackageParts(
            parts.fileFacades.mapTo(mutableListOf()) { relocators.relocatePath(it) },
            parts.multiFileClassParts.entries.associateTo(mutableMapOf()) { (name, facade) ->
              relocators.relocatePath(name) to relocators.relocatePath(facade)
            },
          )
          relocatedPkg to relocatedParts
        }
      }
      val newKmMetadata = KotlinModuleMetadata(newKmModule, kmMetadata.version)

      val newBytes = newKmMetadata.write()
      val relocatedPath = relocators.relocatePath(path)
      val entryName = when {
        relocatedPath != path -> relocatedPath
        // Nothing changed, so keep the original path.
        newBytes.contentEquals(bytes) -> path
        // Content changed but path didn't, so rename to avoid name clash. The filename does not matter to the compiler.
        else -> path.replace(".kotlin_module", ".shadow.kotlin_module")
      }
      val entry = zipEntry(entryName, preserveFileTimestamps, lastModified) {
        unixMode = UnixStat.FILE_FLAG or permissions.toUnixNumeric()
      }
      zipOutStr.putNextEntry(entry)
      zipOutStr.write(newKmMetadata.write())
      zipOutStr.closeEntry()
    }

    private fun transform(fileDetails: FileCopyDetails, path: String): Boolean {
      val transformer = transformers.find { it.canTransformResource(fileDetails) } ?: return false
      fileDetails.file.inputStream().use { inputStream ->
        transformer.transform(
          TransformerContext(
            path = path,
            inputStream = inputStream,
            relocators = relocators,
          ),
        )
      }
      return true
    }

    private fun FileCopyDetails.writeToZip(entryName: String) {
      val entry = zipEntry(entryName, preserveFileTimestamps, lastModified) {
        unixMode = UnixStat.FILE_FLAG or permissions.toUnixNumeric()
      }
      zipOutStr.putNextEntry(entry)
      copyTo(zipOutStr)
      zipOutStr.closeEntry()
    }
  }

  public companion object {
    private val logger = Logging.getLogger(ShadowCopyAction::class.java)

    private val ZipOutputStream.entries: List<ZipEntry>
      get() = this::class.java.getDeclaredField("entries").apply { isAccessible = true }.get(this).cast()

    /**
     * A copy of [org.gradle.api.internal.file.archive.ZipEntryConstants.CONSTANT_TIME_FOR_ZIP_ENTRIES].
     *
     * 1980-02-01 00:00:00 (318182400000).
     */
    public val CONSTANT_TIME_FOR_ZIP_ENTRIES: Long = GregorianCalendar(1980, 1, 1, 0, 0, 0).timeInMillis
  }
}
