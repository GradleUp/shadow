package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class IncludeResourceTransformerTest : BaseTransformerTest<IncludeResourceTransformer>() {
  private lateinit var tempJar: Path

  @BeforeEach
  override fun beforeEach() {
    super.beforeEach()
    tempJar = createTempFile("shade.", ".jar")
  }

  @AfterEach
  fun afterEach() {
    tempJar.deleteExisting()
  }

  @Test
  fun includeResource(@TempDir tempDir: Path) =
    with(transformer) {
      val fooFile = tempDir.resolve("foo").apply { writeText("foo") }
      resource.set("bar")
      file.set(fooFile.toFile())

      assertThat(hasTransformedResource()).isTrue()

      tempJar.outputStream().zipOutputStream().use { modifyOutputStream(it, false) }

      val content = JarPath(tempJar).use { it.getContent("bar") }
      assertThat(content).isEqualTo("foo")
    }

  @Test
  fun hasNotTransformedResource(@TempDir tempDir: Path) =
    with(transformer) {
      val nonExistent = tempDir.resolve("nonexistent").toFile()
      resource.set("bar")
      file.set(nonExistent)

      assertThat(hasTransformedResource()).isFalse()
    }
}
