package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ZipEntryValidationTest {

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
