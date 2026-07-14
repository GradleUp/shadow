package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import java.nio.charset.StandardCharsets
import java.util.Properties
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ZipEntryValidationTest {

  @Test
  fun zipEntryUsesRequestedOrReproducibleTimestampAndAppliesConfiguration() {
    val preserved = zipEntry("file", lastModified = 1234) { comment = "configured" }
    val reproducible = zipEntry("file", preserveLastModified = false, lastModified = 1234)
    val missingTimestamp = zipEntry("file", lastModified = -1)

    assertThat(preserved.time).isEqualTo(1234)
    assertThat(preserved.comment).isEqualTo("configured")
    assertThat(reproducible.time).isEqualTo(missingTimestamp.time)
  }

  @Test
  fun propertiesInputStreamUsesRequestedCharsetAndComments() {
    val properties = Properties().apply { setProperty("greeting", "你好") }

    val content = properties.inputStream(StandardCharsets.UTF_8, "header").reader().readText()

    assertThat(content.startsWith("#header")).isTrue()
    assertThat(content)
      .isEqualTo(content.toByteArray(StandardCharsets.UTF_8).toString(StandardCharsets.UTF_8))
    val loaded = Properties().apply { load(content.reader()) }
    assertThat(loaded.getProperty("greeting")).isEqualTo("你好")
  }

  @Test
  fun validZipEntryNamesDoNotThrow() {
    val validNames =
      listOf(
        "com/example/MyClass.class",
        "META-INF/MANIFEST.MF",
        "assets/icon..png",
        "foo/bar/baz.txt",
        "relative/path/to/resource",
      )

    for (name in validNames) {
      val entry = zipEntry(name)
      assertThat(entry.name).isEqualTo(name)
    }
  }

  @Test
  fun maliciousZipEntryNamesWithPathTraversalThrowException() {
    val maliciousNames =
      listOf(
        "../../../../tmp/pwned.txt",
        "foo/../../bar",
        "foo\\..\\..\\bar",
        "..",
        "../file.txt",
        "..\\file.txt",
        "foo/bar/..",
        "foo/bar/../baz",
      )

    for (name in maliciousNames) {
      val exception =
        assertThrows<GradleException> {
          zipEntry(name)
        }
      assertThat(exception.message)
        .isEqualTo("Malicious ZIP entry containing path traversal sequence: $name")
    }
  }
}
