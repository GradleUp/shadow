package com.github.jengelman.gradle.plugins.shadow.transformers

import java.io.StringReader
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.jdom2.Document
import org.jdom2.JDOMException
import org.jdom2.input.SAXBuilder
import org.jdom2.input.sax.XMLReaders
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource

/**
 * Appends multiple occurrences of some XML file.
 *
 * Modified from `org.apache.maven.plugins.shade.resource.XmlAppendingTransformer.java`
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class XmlAppendingTransformer : Transformer {
  private var doc: Document? = null

  @get:Input
  public var ignoreDtd: Boolean = true

  @get:Optional
  @get:Input
  public var resource: String? = null

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return resource?.equals(element.relativePath.pathString, ignoreCase = true) == true
  }

  override fun transform(context: TransformerContext) {
    val r: Document
    try {
      r = SAXBuilder(XMLReaders.NONVALIDATING).apply {
        expandEntities = false
        if (ignoreDtd) {
          entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
        }
      }.build(context.inputStream)
    } catch (e: JDOMException) {
      throw RuntimeException("Error processing resource $resource: ${e.message}", e)
    }

    if (doc == null) {
      doc = r
    } else {
      val root = r.rootElement
      root.attributes.forEach { a ->
        val mergedEl = doc!!.rootElement
        val mergedAtt = mergedEl.getAttribute(a.name, a.namespace)
        if (mergedAtt == null) {
          mergedEl.setAttribute(a)
        }
      }
      root.children.forEach { n ->
        doc!!.rootElement.addContent(n.clone())
      }
    }
  }

  override fun hasTransformedResource(): Boolean = doc != null

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val entry = ZipEntry(resource)
    entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)
    XMLOutputter(Format.getPrettyFormat()).output(doc, os)
    doc = null
  }

  public companion object {
    public const val XSI_NS: String = "http://www.w3.org/2001/XMLSchema-instance"
  }
}
