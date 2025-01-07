package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import com.github.jengelman.gradle.plugins.shadow.util.JarBuilder
import java.nio.file.Path
import kotlin.io.path.writeText

sealed class BaseTransformerTest : BasePluginTest() {

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

  inline fun buildJar(path: String, builder: JarBuilder.() -> Unit): Path {
    return JarBuilder(path(path)).apply(builder).write()
  }

  fun writeMainClass() {
    path("src/main/java/shadow/Main.java").writeText(
      """
        package shadow;
        public class Main {
          public static void main(String[] args) {
            System.out.println("Hello, World!");
          }
        }
      """.trimIndent(),
    )
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

    inline fun <reified T : Transformer> transform(
      shadowJarBlock: String = "",
      transformerBlock: String = "",
    ): String {
      return """
      $shadowJar {
        $shadowJarBlock
        transform(${T::class.java.name}) {
          $transformerBlock
        }
      }
      """.trimIndent()
    }
  }
}
