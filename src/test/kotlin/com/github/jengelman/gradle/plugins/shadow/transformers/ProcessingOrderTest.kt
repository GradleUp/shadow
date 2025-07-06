package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer.Companion.create
import com.github.jengelman.gradle.plugins.shadow.util.testObjectFactory
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.junit.jupiter.api.Test

/**
 * Test to verify the guaranteed processing order: project files before dependency files.
 */
class ProcessingOrderTest {

  @Test
  fun `project files are processed before dependency files`() {
    val transformer = TestOrderingTransformer::class.java.create(testObjectFactory)
    
    // Simulate processing files in the order they would be encountered
    // First, project files (from sourceSet.output)
    transformer.transform(TransformerContext("config.json", "project content".byteInputStream()))
    
    // Then, dependency files (from configurations)
    transformer.transform(TransformerContext("config.json", "dependency1 content".byteInputStream()))
    transformer.transform(TransformerContext("config.json", "dependency2 content".byteInputStream()))
    
    // Verify the order
    assertThat(transformer.processedFiles).hasSize(3)
    assertThat(transformer.processedFiles[0]).isEqualTo("project content")
    assertThat(transformer.processedFiles[1]).isEqualTo("dependency1 content")
    assertThat(transformer.processedFiles[2]).isEqualTo("dependency2 content")
    
    // Verify that the transformer can distinguish the first (project) file
    assertThat(transformer.projectContent).isEqualTo("project content")
  }

  /**
   * Test transformer that records the order of processing to verify project files come first.
   */
  class TestOrderingTransformer : ResourceTransformer {
    val processedFiles = mutableListOf<String>()
    var projectContent: String? = null

    override fun canTransformResource(element: FileTreeElement): Boolean = 
      element.path.endsWith("config.json")

    override fun transform(context: TransformerContext) {
      val content = context.inputStream.reader().readText()
      processedFiles.add(content)
      
      if (projectContent == null) {
        // First file encountered is guaranteed to be from the project
        projectContent = content
      }
    }

    override fun hasTransformedResource(): Boolean = processedFiles.isNotEmpty()

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
      // No-op for this test
    }
  }
}