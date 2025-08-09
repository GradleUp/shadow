package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import com.github.jengelman.gradle.plugins.shadow.util.JarBuilder
import java.nio.file.Path
import kotlin.io.path.appendText
import org.junit.jupiter.api.BeforeEach

abstract class BaseTransformerTest : BasePluginTest() {
  @BeforeEach
  override fun setup() {
    super.setup()
    projectScript.appendText(
      """
        $shadowJarTask {
          // Most transformers in tests require this to handle duplicate resources.
          duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
      """.trimIndent() + lineSeparator,
    )
  }

  fun buildJarOne(
    builder: JarBuilder.() -> Unit = {
      insert(ENTRY_SERVICES_SHADE, CONTENT_ONE)
      insert(ENTRY_SERVICES_FOO, "one")
    },
  ): Path {
    return buildJar("one.jar", builder)
  }

  fun buildJarTwo(
    builder: JarBuilder.() -> Unit = {
      insert(ENTRY_SERVICES_SHADE, CONTENT_TWO)
      insert(ENTRY_SERVICES_FOO, "two")
    },
  ): Path {
    return buildJar("two.jar", builder)
  }

  protected companion object {
    const val CONTENT_ONE = "one # NOTE: No newline terminates this line/file"
    const val CONTENT_TWO = "two # NOTE: No newline terminates this line/file"
    const val CONTENT_THREE = "three # NOTE: No newline terminates this line/file"
    const val CONTENT_ONE_TWO = "$CONTENT_ONE\n$CONTENT_TWO"

    const val ENTRY_TEST_PROPERTIES = "test.properties"
    const val ENTRY_SERVICES_SHADE = "META-INF/services/org.apache.maven.Shade"
    const val ENTRY_SERVICES_FOO = "META-INF/services/com.acme.Foo"
    const val ENTRY_FOO_SHADE = "META-INF/foo/org.apache.maven.Shade"
  }
}
