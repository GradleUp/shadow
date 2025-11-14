package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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
   * Paths to consider as a license files, evaluated using `it.equals(path, ignoreCase = true)`.
   *
   * Defaults to `META-INF/LICENSE`.
   */
  @get:Input
  public open val paths: SetProperty<String> = objectFactory.setProperty(String::class.java).value(setOf("META-INF/LICENSE"))

  /**
   * Paths to consider as a license files, evaluated using `it.regionMatches(0, path, 0, it.length, ignoreCase = true)`.
   *
   * Defaults to `META-INF/LICENSE.txt` and `META-INF/LICENSE.md`.
   */
  @get:Input
  public open val regionMatchPaths: SetProperty<String> = objectFactory.setProperty(String::class.java).value(setOf("META-INF/LICENSE.txt", "META-INF/LICENSE.md"))

  /**
   * Path of the resulting output file.
   *
   * Defaults to `META-INF/LICENSE`.
   */
  @get:Input
  public open val outputPath: Property<String> = objectFactory.property("META-INF/LICENSE")

  /**
   * Whether to include an empty output, if no input file matches.
   *
   * Defaults to `false`.
   */
  @get:Input
  public open val writeEmpty: Property<Boolean> = objectFactory.property(false)

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
    return paths.get().any {
      it.equals(path, ignoreCase = true)
    } || regionMatchPaths.get().any {
      it.regionMatches(0, path, 0, it.length, ignoreCase = true)
    }
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
    if (!elements.isEmpty()) {
      os.putNextEntry(zipEntry(outputPath.get(), preserveFileTimestamps))
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
  }
}
