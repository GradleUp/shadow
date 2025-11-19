package com.github.jengelman.gradle.plugins.shadow.transformers

import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternSet

/**
 * Transformer to include files with identical content only once in the shadow JAR.
 *
 * Multiple files with the same path but different content lead to an error.
 *
 * Some scenarios for duplicate resources in a shadow jar:
 * * Duplicate `.class` files
 *   Having duplicate `.class` files with different is a situation indicating that the resulting jar is
 *   built with _incompatible_ classes, likely leading to issues during runtime.
 *   This situation can happen when one dependency is (also) included in an uber jar.
 * * Duplicate `META-INF/<group-id>/<artifact-id>/pom.properties`/`xml` files.
 *   Some dependencies contain shaded variants of other dependencies.
 *   Tools that inspect jar files to extract the included dependencies, for example, for license auditing
 *   use cases or tools that collect information of all included dependencies, may rely on these files.
 *   Hence, it is desirable to retain the duplicate resource `pom.properties`/`xml` resources.
 *
 * `DeduplicatingResourceTransformer` checks all entries in the resulting jar.
 * It is generally not recommended to use any of the [include] configuration functions.
 *
 * There are reasons to retain duplicate resources with different contents in the resulting jar.
 * This can be achieved with the [exclude] configuration functions.
 *
 * To exclude a path or pattern from being deduplicated, for example, legit
 * `META-INF/<group-id>/<artifact-id>/pom.properties`/`xml`, configure the transformer with an exclusion
 * like the following:
 * ```kotlin
 * tasks.named<ShadowJar>("shadowJar").configure {
 *   // Keep pom.* files from different Guava versions in the jar.
 *   exclude("META-INF/maven/com.google.guava/guava/pom.*")
 *   // Duplicates with different content for all other resource paths will raise an error.
 * }
 * ```
 *
 * *Tip*: the [com.github.jengelman.gradle.plugins.shadow.tasks.FindResourceInClasspath] convenience task
 * can be used to find resources in a Gradle classpath/configuration.
 *
 * *Warning* Do **not** combine [PreserveFirstFoundResourceTransformer] with this transformer.
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

  internal data class PathInfos(val failOnDuplicateContent: Boolean) {
    val filesPerHash: MutableMap<String, MutableList<File>> = mutableMapOf()

    fun uniqueContentCount() = filesPerHash.size

    fun addFile(hash: String, file: File): Boolean {
      var filesForHash: MutableList<File>? = filesPerHash[hash]
      val new = filesForHash == null
      if (new) {
        filesForHash = mutableListOf()
        filesPerHash[hash] = filesForHash
      }
      filesForHash.add(file)
      return new
    }
  }

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val file = element.file
    val hash = hashForFile(file)

    val pathInfos = sources.computeIfAbsent(element.path) { PathInfos(patternSpec.isSatisfiedBy(element)) }
    val retainInOutput = pathInfos.addFile(hash, file)

    return !retainInOutput
  }

  override fun hasTransformedResource(): Boolean = true

  internal fun duplicateContentViolations(): Map<String, PathInfos> = sources.filter { (_, pathInfos) -> pathInfos.failOnDuplicateContent && pathInfos.uniqueContentCount() > 1 }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val duplicatePaths = duplicateContentViolations()

    if (!duplicatePaths.isEmpty()) {
      val message =
        "Found ${duplicatePaths.size} path duplicate(s) with different content in the shadow JAR:" +
          duplicatePaths
            .map { (path, infos) ->
              "  * $path${infos.filesPerHash.map { (hash, files) ->
                files.joinToString { file -> "    * ${file.path} (Hash: $hash)" }
              }.joinToString("\n", "\n", "")}"
            }
            .joinToString("\n", "\n", "")
      throw GradleException(message)
    }
  }

  // Gradle's configuration uses Java serialization, which cannot serialize `MessageDigest` instances.
  // Using a rather dirty mechanism to memoize the MD instance for task/transformer execution.
  @Transient
  private var digest: MessageDigest? = null

  internal fun hashForFile(file: File): String {
    if (digest == null) {
      digest = MessageDigest.getInstance("SHA-256")
    }
    val d = digest!!
    try {
      // We could replace this block with `o.a.c.codec.digest.DigestUtils.digest(MessageDigest, File)`,
      // but adding a whole new dependency seemed a bit overkill.
      // Using org.apache.commons.io.input.MessageDigestInputStream doesn't give simpler code either.
      file.inputStream().use {
        val buffer = ByteArray(8192)
        var readBytes: Int
        while (it.read(buffer).also { r -> readBytes = r } != -1) {
          d.update(buffer, 0, readBytes)
        }
      }
      return HexFormat.of().formatHex(d.digest())
    } catch (e: Exception) {
      throw RuntimeException("Failed to read data or calculate hash for $file", e)
    }
  }
}
