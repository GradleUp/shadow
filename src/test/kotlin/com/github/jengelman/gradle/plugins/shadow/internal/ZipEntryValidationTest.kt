@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import de.infix.testBalloon.framework.core.testSuite
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipInputStream
import kotlin.io.path.createTempDirectory
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException

val ZipEntryValidationTests by testSuite {
  runTests(::ZipEntryValidationTest)
}

private class ZipEntryValidationTest(val tempDir: Path = createTempDirectory()) {

  fun parentDirectoryEntriesAreOrderedFromRootToLeaf() {
    assertThat("foo/bar/baz.txt".parentDirectoryEntries()).isEqualTo(listOf("foo/", "foo/bar/"))
    assertThat("file.txt".parentDirectoryEntries()).isEqualTo(emptyList())
  }

  fun writeEntryUsesRequestedOrReproducibleTimestampAndAppliesUnixMode() {
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

  fun propertiesInputStreamUsesRequestedCharsetAndComments() {
    val properties = Properties().apply { setProperty("greeting", "你好") }

    val charset = Charsets.UTF_16
    val bytes = properties.inputStream(charset, "header").readBytes()
    val content = String(bytes, charset)

    assertThat(content.startsWith("#header")).isTrue()
    val loaded = Properties().apply { load(bytes.inputStream().reader(charset)) }
    assertThat(loaded.getProperty("greeting")).isEqualTo("你好")
  }

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
      assertFailure {
          ByteArrayOutputStream().zipOutputStream().use { it.writeEntry(name) }
        }
        .isInstanceOf<GradleException>()
        .hasMessage("Malicious ZIP entry containing path traversal sequence: $name")
    }
  }
}
