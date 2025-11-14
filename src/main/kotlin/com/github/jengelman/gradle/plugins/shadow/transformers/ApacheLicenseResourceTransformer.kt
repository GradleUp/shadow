package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * Prevents duplicate copies of the license.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheLicenseResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ApacheLicenseResourceTransformer.java).
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class ApacheLicenseResourceTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : ResourceTransformer by ResourceTransformer.Companion {

  private val elements: MutableSet<String> = LinkedHashSet()

  /**
   * The file encoding of the `LICENSE` file.
   */
  @get:Input
  public open val charsetName: Property<String> = objectFactory.property(Charsets.UTF_8.name())

  /**
   * The separator placed between two licenses.
   */
  @get:Input
  public open val separator: Property<String> = objectFactory.property("\n\n${"-".repeat(120)}\n\n")

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.path
    return LICENSE_PATH.equals(path, ignoreCase = true) ||
      LICENSE_TXT_PATH.regionMatches(0, path, 0, LICENSE_TXT_PATH.length, ignoreCase = true) ||
      LICENSE_MD_PATH.regionMatches(0, path, 0, LICENSE_MD_PATH.length, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    val bytes = context.inputStream.readAllBytes()
    val content = bytes.toString(StandardCharsets.UTF_8).trim('\n', '\r')
    if (!content.isEmpty()) {
      elements.add(content)
    }
  }

  override fun hasTransformedResource(): Boolean = true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    os.putNextEntry(zipEntry(LICENSE_PATH, preserveFileTimestamps))
    var first = true
    val separator = separator.get().toByteArray(StandardCharsets.UTF_8)
    for (element in elements) {
      if (!first) {
        os.write(separator)
      }
      os.write(element.toByteArray(StandardCharsets.UTF_8))
      first = false
    }
    os.closeEntry()
  }

  private companion object {
    private const val LICENSE_PATH = "META-INF/LICENSE"
    private const val LICENSE_TXT_PATH = "META-INF/LICENSE.txt"
    private const val LICENSE_MD_PATH = "META-INF/LICENSE.md"
  }
}
