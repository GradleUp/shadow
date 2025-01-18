package com.github.jengelman.gradle.plugins.shadow.transformers

import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

@CacheableTransformer
public class ApplicationYamlTransformer : Transformer {
  private val parts = mutableListOf<String>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return APPLICATION_YML == element.name
  }

  override fun transform(context: TransformerContext) {
    parts += context.inputStream.bufferedReader().use { it.readText() }
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

  private companion object {
    private const val APPLICATION_YML = "application.yml"
    private const val DOCUMENT_SEPARATOR = "---\n"
  }
}
