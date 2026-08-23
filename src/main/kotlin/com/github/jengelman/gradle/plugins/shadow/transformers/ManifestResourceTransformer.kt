package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.checkDupStrategy
import com.github.jengelman.gradle.plugins.shadow.internal.mapProperty
import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.setProperty
import com.github.jengelman.gradle.plugins.shadow.internal.writeEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateText
import java.io.IOException
import java.io.Serializable
import java.util.jar.Attributes.Name as JarAttributeName
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input

/**
 * A resource processor that allows the arbitrary addition of attributes to the first MANIFEST.MF
 * that is found in the set of JARs being processed, or to a newly created manifest for the shaded
 * JAR.
 *
 * Modified from
 * [org.apache.maven.plugins.shade.resource.ManifestResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ManifestResourceTransformer.java).
 *
 * @author Jason van Zyl
 * @author John Engelman
 */
@CacheableTransformer
public open class ManifestResourceTransformer
@Inject
constructor(final override val objectFactory: ObjectFactory) : ResourceTransformer {
  private var manifestDiscovered = false
  private var manifest: Manifest? = null

  @get:Input public open val mainClass: Property<String> = objectFactory.property("")

  /**
   * Additional manifest entries to add to or remove from `MANIFEST.MF`.
   *
   * Setting an entry's value to [NULL] removes the corresponding attribute from the manifest.
   */
  @get:Input public open val manifestEntries: MapProperty<String, Any> = objectFactory.mapProperty()

  @get:Input
  public open val relocateAttributes: SetProperty<String> =
    objectFactory.setProperty(DEFAULT_RELOCATE_ATTRIBUTES)

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return MANIFEST_NAME.equals(element.path, ignoreCase = true).also { flag ->
      checkDupStrategy(flag, element)
    }
  }

  override fun transform(context: TransformerContext) {
    // We just want to take the first manifest we come across as that's our project's manifest.
    // This is the behavior now which is situational at best. Right now there is no context
    // passed in with the processing so we cannot tell what artifact is being processed.
    if (!manifestDiscovered) {
      try {
        val loadedManifest = Manifest(context.inputStream)
        if (context.relocators.isNotEmpty()) {
          val attributes = loadedManifest.mainAttributes
          for (attribute in relocateAttributes.get()) {
            val attributeValue = attributes.getValue(attribute)
            if (attributeValue != null) {
              val newValue = context.relocators.relocateText(attributeValue)
              attributes.putValue(attribute, newValue)
            }
          }
        }
        manifest = loadedManifest
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
    mainClass.get().takeIf(CharSequence::isNotEmpty)?.let {
      attributes[JarAttributeName.MAIN_CLASS] = it
    }
    manifestEntries.get().forEach { (key, value) ->
      if (value == NULL) {
        attributes.remove(JarAttributeName(key))
      } else {
        attributes.putValue(key, value.toString())
      }
    }

    os.writeEntry(MANIFEST_NAME, preserveFileTimestamps) {
      manifest!!.write(this)
    }
  }

  /**
   * Adds the given attributes to [manifestEntries].
   *
   * If a value is `null`, it will be mapped to [NULL] to remove the attribute from the manifest.
   */
  @Deprecated(
    "Use manifestEntries instead. This method will be removed in Shadow 10.",
    replaceWith = ReplaceWith("manifestEntries.putAll(attributes)"),
  )
  public open fun attributes(attributes: Map<String, *>) {
    attributes.forEach { (key, value) ->
      manifestEntries.put(key, value ?: NULL)
    }
  }

  public companion object {
    private val DEFAULT_RELOCATE_ATTRIBUTES =
      setOf("Export-Package", "Import-Package", "Provide-Capability", "Require-Capability")

    private val logger = Logging.getLogger(ManifestResourceTransformer::class.java)

    /**
     * A sentinel object used in [manifestEntries] or [attributes] to indicate that the specified
     * manifest attribute should be removed from the merged `MANIFEST.MF`.
     */
    @JvmField
    public val NULL: Any =
      object : Serializable {
        @Suppress("unused") // For JavaIoSerializableObjectMustHaveReadResolve.
        private fun readResolve(): Any = NULL
      }
  }
}
