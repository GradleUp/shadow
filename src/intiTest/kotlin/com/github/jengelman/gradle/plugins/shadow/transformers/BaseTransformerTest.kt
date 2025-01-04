package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import com.github.jengelman.gradle.plugins.shadow.util.AppendableJar
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.writeText

sealed class BaseTransformerTest : BasePluginTest() {

  fun buildJarOne(
    builder: AppendableJar.() -> AppendableJar = {
      insert(ENTRY_SERVICES_SHADE, CONTENT_ONE)
      insert(ENTRY_SERVICES_FOO, "one")
    },
  ): Path {
    return buildJar("one.jar")
      .builder()
      .write()
  }

  fun buildJarTwo(
    builder: AppendableJar.() -> AppendableJar = {
      insert(ENTRY_SERVICES_SHADE, CONTENT_TWO)
      insert(ENTRY_SERVICES_FOO, "two")
    },
  ): Path {
    return buildJar("two.jar")
      .builder()
      .write()
  }

  fun buildJar(path: String): AppendableJar {
    return AppendableJar(path(path))
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
    const val ENTRY_SERVICE_EXTENSION_MODULE = "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
    const val ENTRY_GROOVY_EXTENSION_MODULE = "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule"

    fun getJarFileContents(jarPath: Path, entryName: String): String {
      JarFile(jarPath.toFile()).use { jar ->
        val entry = jar.getJarEntry(entryName) ?: error("Entry not found: $entryName")
        return jar.getInputStream(entry).bufferedReader().readText()
      }
    }

    inline fun <reified T : Transformer> transform(
      shadowBlock: String = "",
      transformerBlock: String = "",
    ): String {
      return """
      $shadowJar {
        $shadowBlock
        transform(${T::class.java.name}) {
          $transformerBlock
        }
      }
      """.trimIndent()
    }
  }
}
