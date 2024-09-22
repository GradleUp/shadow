

package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.codehaus.plexus.util.xml.XmlStreamReader
import org.codehaus.plexus.util.xml.XmlStreamWriter
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.codehaus.plexus.util.xml.Xpp3DomWriter
import org.gradle.api.file.FileTreeElement

/**
 * A resource processor that aggregates plexus <code>components.xml</code> files.
 *
 * Modified from `org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformer.java`.
 *
 * @author John Engelman
 */
class ComponentsXmlResourceTransformer : Transformer {
  private val components: MutableMap<String, Xpp3Dom> = LinkedHashMap()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    return COMPONENTS_XML_PATH == path
  }

  override fun transform(context: TransformerContext) {
    val newDom: Xpp3Dom = try {
      val bis = object : BufferedInputStream(context.inputStream) {
        @Throws(IOException::class)
        override fun close() {
          // leave ZIP open
        }
      }
      Xpp3DomBuilder.build(XmlStreamReader(bis))
    } catch (e: Exception) {
      throw IOException("Error parsing components.xml in ${context.inputStream}", e)
    }

    // Only try to merge in components if there are some elements in the component-set
    if (newDom.getChild("components") == null) return

    val children = newDom.getChild("components").getChildren("component")

    for (component in children) {
      var role = getValue(component, "role")
      role = getRelocatedClass(role, context)
      setValue(component, "role", role)

      val roleHint = getValue(component, "role-hint")

      var impl = getValue(component, "implementation")
      impl = getRelocatedClass(impl, context)
      setValue(component, "implementation", impl)

      val key = "$role:$roleHint"
      if (components.containsKey(key)) {
        // TODO: use the tools in Plexus to merge these properly. For now, I just need an all-or-nothing
        // configuration carry over

        val dom = components[key]
        if (dom?.getChild("configuration") != null) {
          component.addChild(dom.getChild("configuration"))
        }
      }

      val requirements = component.getChild("requirements")
      if (requirements != null && requirements.childCount > 0) {
        for (r in requirements.childCount - 1 downTo 0) {
          val requirement = requirements.getChild(r)

          var requiredRole = getValue(requirement, "role")
          requiredRole = getRelocatedClass(requiredRole, context)
          setValue(requirement, "role", requiredRole)
        }
      }

      components[key] = component
    }
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val data = getTransformedResource()
    val entry = ZipEntry(COMPONENTS_XML_PATH)
    entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)
    data.inputStream().copyTo(os)
    components.clear()
  }

  override fun hasTransformedResource(): Boolean = components.isNotEmpty()

  @Throws(IOException::class)
  private fun getTransformedResource(): ByteArray {
    val baos = ByteArrayOutputStream(1024 * 4)
    XmlStreamWriter(baos).use { writer ->
      val dom = Xpp3Dom("component-set")
      val componentDom = Xpp3Dom("components")
      dom.addChild(componentDom)

      for (component in components.values) {
        componentDom.addChild(component)
      }

      Xpp3DomWriter.write(writer, dom)
    }
    return baos.toByteArray()
  }

  private fun getRelocatedClass(className: String?, context: TransformerContext): String {
    val relocators = context.relocators
    val stats = context.stats
    if (!className.isNullOrEmpty()) {
      for (relocator in relocators) {
        if (relocator.canRelocateClass(className)) {
          val relocateClassContext = RelocateClassContext(className, stats)
          return relocator.relocateClass(relocateClassContext)
        }
      }
    }
    return className.orEmpty()
  }

  private fun getValue(dom: Xpp3Dom, element: String): String {
    val child = dom.getChild(element)
    return child?.value.orEmpty()
  }

  private fun setValue(dom: Xpp3Dom, element: String, value: String) {
    val child = dom.getChild(element)
    if (child != null && value.isNotEmpty()) {
      child.value = value
    }
  }

  companion object {
    const val COMPONENTS_XML_PATH = "META-INF/plexus/components.xml"
  }
}
