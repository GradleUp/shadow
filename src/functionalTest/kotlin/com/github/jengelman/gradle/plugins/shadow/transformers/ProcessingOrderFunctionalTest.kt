package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

/**
 * Functional test to verify ResourceTransformer processing order:
 * project files are processed before dependency files.
 */
class ProcessingOrderFunctionalTest : BasePluginTest() {

  @Test
  fun `project files processed before dependency files in real build`() {
    // Create a test configuration file in the project
    val configFile = path("src/main/resources/test-config.json")
    configFile.parent.toFile().mkdirs()
    configFile.writeText("""{"source": "project", "id": "my-project", "data": []}""")
    
    // Create dependency JARs with configuration files
    val dep1 = buildJarOne {
      insert("test-config.json", """{"source": "dependency1", "data": ["dep1-item"]}""")
    }
    val dep2 = buildJarTwo {
      insert("test-config.json", """{"source": "dependency2", "data": ["dep2-item"]}""") 
    }

    // Configure the build with our test transformer
    projectScriptPath.appendText(
      """
        dependencies {
          implementation files('${dep1.toString().replace('\\', '\\\\')}')
          implementation files('${dep2.toString().replace('\\', '\\\\')}')
        }
        
        import ${TestConfigMergingTransformer::class.qualifiedName}
        
        shadowJar {
          transform(TestConfigMergingTransformer)
        }
      """.trimIndent()
    )

    run(shadowJarTask)

    // Verify the transformer received files in the correct order
    assertThat(outputShadowJar).useAll {
      containsEntries("merged-config.json")
    }
    
    val mergedContent = outputShadowJar.use { jar ->
      jar.getStream("merged-config.json").reader().readText()
    }
    
    // The merged content should preserve the project's structure and include data from dependencies
    assertThat(mergedContent).isEqualTo("""{"source": "project", "id": "my-project", "data": ["dep1-item", "dep2-item"]}""")
  }
}