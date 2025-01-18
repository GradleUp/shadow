package com.github.jengelman.gradle.plugins.shadow.transformers

import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

@CacheableTransformer
public class ApplicationYamlTransformer : Transformer {
  private val parts = mutableListOf<String>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return element.name.equals(APPLICATION_YML, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    parts += context.inputStream.bufferedReader().use { it.readText() }.trim()
  }

  override fun hasTransformedResource(): Boolean {
    return parts.isNotEmpty()
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    os.putNextEntry(ZipEntry(APPLICATION_YML))
    parts.joinToString(DOCUMENT_SEPARATOR).byteInputStream().use {
      it.copyTo(os)
    }
    os.closeEntry()
  }

  public companion object {
    public const val APPLICATION_YML: String = "application.yml"
    private const val DOCUMENT_SEPARATOR = "\n---\n"
  }
}
