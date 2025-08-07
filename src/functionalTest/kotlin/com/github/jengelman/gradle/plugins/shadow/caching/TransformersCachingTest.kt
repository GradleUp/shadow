package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class TransformersCachingTest : BaseCachingTest() {
  @Test
  fun serviceFileTransformerPropsChanged() {
    val mainClassEntry = writeClass()
    val assertions = {
      assertCompositeExecutions {
        containsOnly(
          "my/",
          mainClassEntry,
          *manifestEntries,
        )
      }
    }

    assertions()

    projectScriptPath.appendText(
      transform<ServiceFileTransformer>(
        transformerBlock = """
          path = 'META-INF/foo'
        """.trimIndent(),
      ),
    )

    assertions()

    val replaced = projectScriptPath.readText().replace("META-INF/foo", "META-INF/bar")
    projectScriptPath.writeText(replaced)

    assertions()
  }

  @Test
  fun disableCacheIfAnyTransformerIsNotCacheable() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          mergeServiceFiles()
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions()

    projectScriptPath.appendText(
      """
        $shadowJar {
          mergeGroovyExtensionModules()
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions()

    projectScriptPath.appendText(
      """
        $shadowJar {
          // Use Transformer.Companion (no-op) to mock a custom transformer here, it's not cacheable.
          transform(${ResourceTransformer.Companion::class.java.name})
        }
      """.trimIndent(),
    )

    assertExecutionSuccess()
    cleanOutputs()
    // The shadowJar task should be executed again as the cache is disabled.
    assertExecutionSuccess()
  }
}
