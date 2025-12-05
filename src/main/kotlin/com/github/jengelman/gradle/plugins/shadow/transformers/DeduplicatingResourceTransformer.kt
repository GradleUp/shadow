package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.tasks.FindResourceInClasspath
import java.io.File
import javax.inject.Inject
import org.apache.commons.codec.digest.DigestUtils
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternSet

/**
 * Transformer to include files with identical content only once in the shadowed JAR.
 *
 * Multiple files with the same path but different content lead to an error.
 *
 * Some scenarios for duplicate resources in a shadow jar:
 *
 * - Duplicate `.class` files
 *   Having duplicate `.class` files with different content is a situation indicating that the resulting jar is
 *   built with _incompatible_ classes, likely leading to issues during runtime.
 *   This situation can happen when one dependency is (also) included in an uber jar.
 *
 * - Duplicate `META-INF/<group-id>/<artifact-id>/pom.properties`/`xml` files.
 *   Some dependencies contain shaded variants of other dependencies.
 *   Tools that inspect jar files to extract the included dependencies, for example, for license auditing
 *   use cases or tools that collect information of all included dependencies, may rely on these files.
 *   Hence, it is desirable to retain the duplicate resource `pom.properties`/`xml` resources.
 *
 * [DeduplicatingResourceTransformer] checks all entries in the resulting jar.
 * It is generally not recommended to use any of the [include] configuration functions.
 *
 * There are reasons to retain duplicate resources with different contents in the resulting jar.
 * This can be achieved with the [exclude] configuration functions.
 *
 * To exclude a path or pattern from being deduplicated, for example, legit
 * `META-INF/<group-id>/<artifact-id>/pom.properties`/`xml`, configure the transformer with an exclusion
 * like the following:
 *
 * ```kotlin
 * tasks.shadowJar {
 *   transform(DeduplicatingResourceTransformer::class.java) {
 *     // Keep pom.* files from different Guava versions in the jar.
 *     exclude("META-INF/maven/com.google.guava/guava/pom.*")
 *     // Duplicates with different content for all other resource paths will raise an error.
 *   }
 * }
 * ```
 *
 * *Tip*: the [FindResourceInClasspath] convenience task can be used to find resources in a Gradle
 * classpath/configuration.
 *
 * *Warning* Do **not** combine [PreserveFirstFoundResourceTransformer] with this transformer,
 * as they handle duplicates differently and combining them would lead to redundant or unexpected behavior.
 */
@CacheableTransformer
public open class DeduplicatingResourceTransformer(
  final override val objectFactory: ObjectFactory,
  patternSet: PatternSet,
) : PatternFilterableResourceTransformer(patternSet) {
  @get:Internal
  internal val sources: MutableMap<String, PathInfos> = mutableMapOf()

  @Inject
  public constructor(objectFactory: ObjectFactory) : this(objectFactory, PatternSet())

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val file = element.file
    val hash = file.sha256Hex()

    val pathInfos = sources.computeIfAbsent(element.path) {
      PathInfos(patternSpec.isSatisfiedBy(element))
    }
    val retainInOutput = pathInfos.addFile(hash, file)

    return !retainInOutput
  }

  override fun hasTransformedResource(): Boolean = true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val duplicatePaths = duplicateContentViolations()

    if (duplicatePaths.isNotEmpty()) {
      val message = buildString {
        append("Found ${duplicatePaths.size} path duplicate(s) with different content in the shadowed JAR:\n")
        duplicatePaths.forEach { (path, infos) ->
          append("  * $path\n")
          infos.filesPerHash.forEach { (hash, files) ->
            files.forEach { file ->
              append("    * ${file.path} (SHA256: $hash)\n")
            }
          }
        }
      }
      throw GradleException(message)
    }
  }

  internal fun duplicateContentViolations(): Map<String, PathInfos> = sources.filter { (_, pathInfos) ->
    pathInfos.failOnDuplicateContent && pathInfos.uniqueContentCount() > 1
  }

  internal data class PathInfos(val failOnDuplicateContent: Boolean) {
    val filesPerHash: MutableMap<String, MutableList<File>> = mutableMapOf()

    fun uniqueContentCount() = filesPerHash.size

    fun addFile(hash: String, file: File): Boolean {
      val new = hash !in filesPerHash
      filesPerHash.getOrPut(hash) { mutableListOf() }.add(file)
      return new
    }
  }

  internal companion object {
    fun File.sha256Hex(): String {
      try {
        return inputStream().use {
          DigestUtils.sha256Hex(it)
        }
      } catch (e: Exception) {
        throw RuntimeException("Failed to read data or calculate hash for $this", e)
      }
    }
  }
}
