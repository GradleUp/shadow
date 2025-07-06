package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Test transformer that demonstrates the guaranteed processing order:
 * project files first, then dependency files.
 */
@CacheableTransformer
class TestConfigMergingTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : ResourceTransformer {
  
  private var projectContent: String? = null
  private val allDataItems = mutableListOf<String>()

  override fun canTransformResource(element: FileTreeElement): Boolean = 
    element.path == "test-config.json"

  override fun transform(context: TransformerContext) {
    val content = context.inputStream.reader().readText()
    
    if (projectContent == null) {
      // First file is guaranteed to be from the project - preserve it
      projectContent = content
    }
    
    // Extract data items from all files (simple string parsing for test)
    // For example: {"source": "dependency1", "data": ["dep1-item"]}
    val dataMatch = Regex(""""data":\s*\[\s*"([^"]+)"\s*\]""").find(content)
    dataMatch?.groupValues?.get(1)?.let { allDataItems.add(it) }
  }

  override fun hasTransformedResource(): Boolean = projectContent != null

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    if (projectContent != null) {
      // Create merged content preserving project structure but adding all data items
      val dataItemsJson = allDataItems.joinToString("\", \"", "\"", "\"")
      val merged = """{"source": "project", "id": "my-project", "data": [$dataItemsJson]}"""
      
      val entry = zipEntry("merged-config.json", preserveFileTimestamps, System.currentTimeMillis())
      os.putNextEntry(entry)
      os.write(merged.toByteArray())
      os.closeEntry()
    }
  }
}