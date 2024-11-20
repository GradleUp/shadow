package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext.Companion.getEntryTimestamp
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
 * A resource processor that aggregates plexus `components.xml` files.
 *
 * Modified from `org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformer.java`
 *
 * @author John Engelman
 */
open class ComponentsXmlResourceTransformer : Transformer {
  private val components = mutableMapOf<String, Xpp3Dom>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return COMPONENTS_XML_PATH == element.relativePath.pathString
  }

  override fun transform(context: TransformerContext) {
    val newDom = try {
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
      var role: String? = getValue(component, "role")
      role = getRelocatedClass(role, context)
      setValue(component, "role", role)

      val roleHint = getValue(component, "role-hint")

      var impl: String? = getValue(component, "implementation")
      impl = getRelocatedClass(impl, context)
      setValue(component, "implementation", impl)

      val key = "$role:$roleHint"
      // TODO: use the tools in Plexus to merge these properly. For now, I just need an all-or-nothing
      // configuration carry over
      components[key]?.getChild("configuration")?.let {
        component.addChild(it)
      }

      val requirements = component.getChild("requirements")
      if (requirements != null && requirements.childCount > 0) {
        for (r in requirements.childCount - 1 downTo 0) {
          val requirement = requirements.getChild(r)
          var requiredRole: String? = getValue(requirement, "role")
          requiredRole = getRelocatedClass(requiredRole, context)
          setValue(requirement, "role", requiredRole)
        }
      }
      components[key] = component
    }
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val entry = ZipEntry(COMPONENTS_XML_PATH)
    entry.time = getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)

    transformedResource.inputStream().use {
      it.copyTo(os)
    }
    components.clear()
  }

  override fun hasTransformedResource(): Boolean = components.isNotEmpty()

  @get:Throws(IOException::class)
  private val transformedResource: ByteArray
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

  companion object {
    const val COMPONENTS_XML_PATH: String = "META-INF/plexus/components.xml"

    private fun getRelocatedClass(className: String?, context: TransformerContext): String? {
      val stats = context.stats
      if (!className.isNullOrEmpty()) {
        for (relocator in context.relocators) {
          if (relocator.canRelocateClass(className)) {
            val relocateClassContext = RelocateClassContext(className, stats)
            return relocator.relocateClass(relocateClassContext)
          }
        }
      }
      return className
    }

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
