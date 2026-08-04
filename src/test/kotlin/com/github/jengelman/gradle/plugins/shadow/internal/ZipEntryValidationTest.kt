package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipInputStream
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class ZipEntryValidationTest {

  @Test
  fun parentDirectoryEntriesAreOrderedFromRootToLeaf() {
    assertThat("foo/bar/baz.txt".parentDirectoryEntries()).isEqualTo(listOf("foo/", "foo/bar/"))
    assertThat("file.txt".parentDirectoryEntries()).isEqualTo(emptyList())
  }

  @Test
  fun writeEntryUsesRequestedOrReproducibleTimestampAndAppliesUnixMode(@TempDir tempDir: Path) {
    val requestedTimestamp = 1_700_000_000_000
    val archive = tempDir.resolve("output.jar").toFile()
    ZipOutputStream(archive).use { output ->
      output.writeEntry(
        "preserved",
        lastModified = requestedTimestamp,
        unixMode = UnixMode.file(),
      )
      output.writeEntry("directory/", unixMode = UnixMode.directory(448))
      output.writeEntry(
        "reproducible",
        preserveLastModified = false,
        lastModified = requestedTimestamp,
      )
      output.writeEntry("missing-timestamp", lastModified = -1)
    }

    ZipFile(archive).use { zip ->
      val preserved = zip.getEntry("preserved")
      val directory = zip.getEntry("directory/")
      val reproducible = zip.getEntry("reproducible")
      val missingTimestamp = zip.getEntry("missing-timestamp")
      assertThat(preserved.time).isEqualTo(requestedTimestamp)
      assertThat(preserved.unixMode).isEqualTo(UnixStat.FILE_FLAG or UnixStat.DEFAULT_FILE_PERM)
      assertThat(directory.unixMode).isEqualTo(UnixStat.DIR_FLAG or 448)
      assertThat(reproducible.time).isEqualTo(missingTimestamp.time)
    }
  }

  @Test
  fun propertiesInputStreamUsesRequestedCharsetAndComments() {
    val properties = Properties().apply { setProperty("greeting", "你好") }

    val charset = Charsets.UTF_16
    val bytes = properties.inputStream(charset, "header").readBytes()
    val content = String(bytes, charset)

    assertThat(content.startsWith("#header")).isTrue()
    val loaded = Properties().apply { load(bytes.inputStream().reader(charset)) }
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

    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
      for (name in validNames) {
        zip.writeEntry(name)
      }
    }

    val names =
      ZipInputStream(output.toByteArray().inputStream()).use { zis ->
        val names = mutableSetOf<String>()
        while (true) {
          val entry = zis.nextEntry ?: break
          names += entry.name
        }
        names
      }

    assertThat(names).isEqualTo(validNames.toSet())
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
          ByteArrayOutputStream().zipOutputStream().use { it.writeEntry(name) }
        }
      assertThat(exception.message)
        .isEqualTo("Malicious ZIP entry containing path traversal sequence: $name")
    }
  }
}
