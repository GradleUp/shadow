package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * A resource processor that allows the addition of an arbitrary file content into the shaded JAR.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.IncludeResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/IncludeResourceTransformer.java).
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class IncludeResourceTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : Transformer by NoOpTransformer {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public open val file: RegularFileProperty = objectFactory.fileProperty()

  @get:Input
  public open val resource: Property<String> = objectFactory.property()

  override fun hasTransformedResource(): Boolean = file.get().asFile.exists()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    os.putNextEntry(zipEntry(resource.get(), preserveFileTimestamps))

    file.get().asFile.inputStream().use { inputStream ->
      inputStream.copyTo(os)
    }
  }
}
