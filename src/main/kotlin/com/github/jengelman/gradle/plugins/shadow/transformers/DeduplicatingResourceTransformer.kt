package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.WithPatternFilterable
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Transformer to include files with identical content only once in the shadow JAR.
 *
 * Multiple files with the same path but different content lead to an error.
 */
@CacheableTransformer
public open class DeduplicatingResourceTransformer
@Inject
constructor(
  final override val objectFactory: ObjectFactory,
) : WithPatternFilterable(canBeEmpty = true),
  ResourceTransformer by ResourceTransformer.Companion {
  private val sources: MutableMap<String, MutableMap<Long, MutableList<File>>> = LinkedHashMap()

  /**
   * Flag whether the transformer should *not* fail when detecting duplicate paths with different
   * content. Defaults to `false`.
   */
  @get:Input public val dontFail: Property<Boolean> = objectFactory.property(Boolean::class.java).value(false)

  override fun canTransformResource(element: FileTreeElement): Boolean {
    if (!patternSpec.isSatisfiedBy(element)) {
      return false
    }

    val perPathPerHashFiles = sources.computeIfAbsent(element.path) { LinkedHashMap() }

    val file = element.file
    val hash = hashForFile(file)
    val withSameContent = perPathPerHashFiles.computeIfAbsent(hash) { mutableListOf() }
    withSameContent.add(file)

    return perPathPerHashFiles.size > 1 || withSameContent.size > 1
  }

  override fun hasTransformedResource(): Boolean = true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val duplicatePaths: MutableMap<String, MutableMap<Long, MutableList<File>>> = LinkedHashMap()

    sources.forEach { (path, filesByHash) ->
      if (filesByHash.size == 1) {
        val singleHashFiles = filesByHash.values.first()
        if (singleHashFiles.size > 1) {
          // No need to add the file, it's already implicitly there due to the initial 'return
          // false' of canTransformResource()
          logger.info(
            "Skipping {} input files with identical content for path {}",
            singleHashFiles.size - 1,
            path,
          )
        }
      } else {
        duplicatePaths[path] = filesByHash
      }
    }

    if (!duplicatePaths.isEmpty()) {
      val message =
        "Found ${duplicatePaths.size} path duplicate(s) with different content in the shadow JAR:" +
          duplicatePaths
            .map { (path, filesByHash) ->
              "  * $path${filesByHash.map { (hash, files) ->
                files.joinToString { file -> "    * ${file.path} (Hash: $hash)" }
              }.joinToString("\n", "\n", "")}"
            }
            .joinToString("\n", "\n", "")
      if (dontFail.get()) {
        logger.error(message)
      } else {
        throw GradleException(message)
      }
    }
  }

  private val digest: MessageDigest by lazy { MessageDigest.getInstance("SHA-256") }

  private fun hashForFile(file: File): Long {
    try {
      file.inputStream().use {
        val buffer = ByteArray(8192)
        while (true) {
          val rd = it.read(buffer)
          if (rd == -1) {
            break
          }
          digest.update(buffer, 0, rd)
        }
      }
      return ByteBuffer.wrap(digest.digest()).getLong(0)
    } catch (e: Exception) {
      throw RuntimeException("Failed to read data or calculate hash for $file", e)
    }
  }

  private companion object {
    private val logger: Logger =
      LoggerFactory.getLogger(DeduplicatingResourceTransformer::class.java)
  }
}
