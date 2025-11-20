package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.apache.tools.zip.ZipOutputStream
import org.codehaus.plexus.util.xml.XmlStreamReader
import org.codehaus.plexus.util.xml.XmlStreamWriter
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.codehaus.plexus.util.xml.Xpp3DomWriter
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Internal

/**
 * A resource processor that aggregates plexus `components.xml` files.
 *
 * Modified from
 * [org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ComponentsXmlResourceTransformer.java).
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class ComponentsXmlResourceTransformer : ResourceTransformer {
  private val components = mutableMapOf<String, Xpp3Dom>()

  @get:Internal
  internal val transformedResource: ByteArray
    get() {
      val os = ByteArrayOutputStream(1024 * 4)
      XmlStreamWriter(os).use { writer ->
        val dom = Xpp3Dom("component-set")
        val componentDom = Xpp3Dom("components")
        dom.addChild(componentDom)
        for (component in components.values) {
          componentDom.addChild(component)
        }
        Xpp3DomWriter.write(writer, dom)
      }
      return os.toByteArray()
    }

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return COMPONENTS_XML_PATH == element.path
  }

  override fun transform(context: TransformerContext) {
    val newDom =
      try {
        val bis =
          object : BufferedInputStream(context.inputStream) {
            @Throws(IOException::class)
            override fun close() {
              // Leave ZIP open.
            }
          }
        Xpp3DomBuilder.build(XmlStreamReader(bis))
      } catch (e: Exception) {
        throw IOException("Error parsing components.xml in ${context.inputStream}", e)
      }

    // Only try to merge in components if there are some elements in the component-set.
    if (newDom.getChild("components") == null) return

    val children = newDom.getChild("components").getChildren("component")
    for (component in children) {
      val role = getValue(component, "role").let { context.relocators.relocateClass(it) }
      setValue(component, "role", role)

      val roleHint = getValue(component, "role-hint")

      val impl = getValue(component, "implementation").let { context.relocators.relocateClass(it) }
      setValue(component, "implementation", impl)

      val key = "$role:$roleHint"
      // TODO: use the tools in Plexus to merge these properly. For now, I just need an
      // all-or-nothing.
      // Configuration carry over.
      components[key]?.getChild("configuration")?.let { component.addChild(it) }

      val requirements = component.getChild("requirements")
      if (requirements != null && requirements.childCount > 0) {
        for (r in requirements.childCount - 1 downTo 0) {
          val requirement = requirements.getChild(r)
          val requiredRole =
            getValue(requirement, "role").let { context.relocators.relocateClass(it) }
          setValue(requirement, "role", requiredRole)
        }
      }
      components[key] = component
    }
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    os.putNextEntry(zipEntry(COMPONENTS_XML_PATH, preserveFileTimestamps))
    os.write(transformedResource)
    components.clear()
    os.closeEntry()
  }

  override fun hasTransformedResource(): Boolean = components.isNotEmpty()

  public companion object {
    public const val COMPONENTS_XML_PATH: String = "META-INF/plexus/components.xml"

    private fun getValue(dom: Xpp3Dom, element: String): String {
      return dom.getChild(element).value.orEmpty()
    }

    private fun setValue(dom: Xpp3Dom, element: String, value: String?) {
      val child = dom.getChild(element)
      if (child == null || value.isNullOrEmpty()) return
      child.value = value
    }
  }
}
