package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * A resource processor that appends content for a resource, separated by a newline.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.AppendingTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/AppendingTransformer.java).
 *
 * @author John Engelman
 */
@CacheableTransformer
@Suppress("ktlint:standard:backing-property-naming")
public open class AppendingTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : ResourceTransformer {
  /**
   * Defer initialization, see [issue 763](https://github.com/GradleUp/shadow/issues/763).
   */
  private var _data: ByteArrayOutputStream? = null
  private inline val data get() = _data ?: ByteArrayOutputStream().also { _data = it }

  @get:Optional
  @get:Input
  public open val resource: Property<String> = objectFactory.property()

  @get:Input
  public open val separator: Property<String> = objectFactory.property(DEFAULT_SEPARATOR)

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return resource.orNull.equals(element.path, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    if (data.size() > 0) {
      // Append the separator before the new content to ensure the separator is not at the end of the file.
      data.write(separator.get().toByteArray())
    }
    context.inputStream.copyTo(data)
  }

  override fun hasTransformedResource(): Boolean {
    return data.size() > 0
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    os.putNextEntry(zipEntry(resource.get(), preserveFileTimestamps))

    // Closing a ByteArrayOutputStream has no effect, so we don't use a use block here.
    data.toByteArray().inputStream().copyTo(os)
    data.reset()
  }

  public companion object {
    public const val DEFAULT_SEPARATOR: String = "\n"
  }
}
