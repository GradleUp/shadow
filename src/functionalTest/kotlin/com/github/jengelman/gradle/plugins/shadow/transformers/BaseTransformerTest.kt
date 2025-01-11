package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import com.github.jengelman.gradle.plugins.shadow.util.JarBuilder
import java.nio.file.Path

abstract class BaseTransformerTest : BasePluginTest() {
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

  inline fun buildJar(relative: String, builder: JarBuilder.() -> Unit): Path {
    return JarBuilder(path("temp/$relative")).apply(builder).write()
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
