package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.mapProperty
import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import java.io.IOException
import java.util.jar.Attributes as JarAttribute
import java.util.jar.JarFile
import java.util.jar.Manifest
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * A resource processor that allows the arbitrary addition of attributes to
 * the first MANIFEST.MF that is found in the set of JARs being processed, or
 * to a newly created manifest for the shaded JAR.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.ManifestResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ManifestResourceTransformer.java).
 *
 * @author Jason van Zyl
 * @author John Engelman
 */
@CacheableTransformer
public open class ManifestResourceTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : ResourceTransformer {
  private var manifestDiscovered = false
  private var manifest: Manifest? = null

  @get:Optional
  @get:Input
  public open val mainClass: Property<String> = objectFactory.property()

  @get:Optional
  @get:Input
  public open val manifestEntries: MapProperty<String, JarAttribute> = objectFactory.mapProperty()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return JarFile.MANIFEST_NAME.equals(element.path, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    // We just want to take the first manifest we come across as that's our project's manifest. This is the behavior
    // now which is situational at best. Right now there is no context passed in with the processing so we cannot
    // tell what artifact is being processed.
    if (!manifestDiscovered) {
      try {
        manifest = Manifest(context.inputStream)
        manifestDiscovered = true
      } catch (e: IOException) {
        logger.warn("Failed to read MANIFEST.MF", e)
      }
    }
  }

  override fun hasTransformedResource(): Boolean = true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    // If we didn't find a manifest, then let's create one.
    if (manifest == null) {
      manifest = Manifest()
    }

    val attributes = manifest!!.mainAttributes
    mainClass.orNull?.let {
      attributes[mainClassAttributeKey] = it
    }
    manifestEntries.get().forEach { (key, value) ->
      attributes[JarAttribute.Name(key)] = value
    }

    os.putNextEntry(zipEntry(JarFile.MANIFEST_NAME, preserveFileTimestamps))
    manifest!!.write(os)
  }

  public open fun attributes(attributes: Map<String, JarAttribute>) {
    manifestEntries.putAll(attributes)
  }

  private companion object {
    private val logger = Logging.getLogger(ManifestResourceTransformer::class.java)
  }
}
