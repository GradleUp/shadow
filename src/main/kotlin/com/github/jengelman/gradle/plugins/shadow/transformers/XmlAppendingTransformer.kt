

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

@CacheableTransformer
class XmlAppendingTransformer : Transformer {

  @Input
  var ignoreDtd: Boolean = true

  @Optional
  @Input
  var resource: String? = null

  private var doc: Document? = null

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    return resource?.equals(path, ignoreCase = true) == true
  }

  override fun transform(context: TransformerContext) {
    val r: Document
    try {
      val builder = SAXBuilder(XMLReaders.NONVALIDATING).apply {
        expandEntities = false
        if (ignoreDtd) {
          entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
        }
      }
      r = builder.build(context.inputStream)
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
    val entry = ZipEntry(resource).apply {
      time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, time)
    }
    os.putNextEntry(entry)
    XMLOutputter(Format.getPrettyFormat()).output(doc, os)
    doc = null
  }
}
