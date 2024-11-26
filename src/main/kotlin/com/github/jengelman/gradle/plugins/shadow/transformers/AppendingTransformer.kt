package com.github.jengelman.gradle.plugins.shadow.transformers

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.apache.tools.zip.ZipEntry
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
  override val objectFactory: ObjectFactory,
) : Transformer {
  /**
   * Defer initialization, see [issue 763](https://github.com/GradleUp/shadow/issues/763).
   */
  private var _data: ByteArrayOutputStream? = null
  private inline val data get() = if (_data == null) ByteArrayOutputStream().also { _data = it } else _data!!

  @get:Optional
  @get:Input
  public val resource: Property<String> = objectFactory.property(String::class.java)

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return resource.orNull.equals(element.relativePath.pathString, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    context.inputStream.use {
      it.copyTo(data)
      data.write('\n'.code)
    }
  }

  override fun hasTransformedResource(): Boolean {
    return data.size() > 0
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val entry = ZipEntry(resource.get())
    entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)

    data.toByteArray().inputStream().copyTo(os)
    data.reset()
  }
}
