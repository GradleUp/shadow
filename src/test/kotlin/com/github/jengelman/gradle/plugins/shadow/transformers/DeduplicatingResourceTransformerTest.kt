package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DeduplicatingResourceTransformerTest : BaseTransformerTest<DeduplicatingResourceTransformer>() {

  @TempDir
  lateinit var tempDir: Path

  private lateinit var file1: File
  private lateinit var file2: File
  private lateinit var file3: File

  private var hash1 = ""
  private var hash2 = ""
  private var hash3 = ""

  @BeforeEach
  fun setupFiles() {
    val content1 = "content1"
    val content2 = "content2"

    file1 = tempDir.resolve("file1").toFile().apply {
      writeText(content1)
    }
    file2 = tempDir.resolve("file2").toFile().apply {
      writeText(content1)
    }
    file3 = tempDir.resolve("file3").toFile().apply {
      writeText(content2)
    }

    hash1 = transformer.hashForFile(file1)
    hash2 = transformer.hashForFile(file2)
    hash3 = transformer.hashForFile(file3)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun duplicateContent(exclusionCheck: Boolean) {
    with(transformer) {
      if (!exclusionCheck) {
        exclude("multiple-contents")
      }

      // new path, new file content --> retain resource
      assertThat(canTransformResource("multiple-contents", file1)).isFalse()
      // same path, same file content --> skip resource
      assertThat(canTransformResource("multiple-contents", file2)).isTrue()
      // same path, different file content --> retain resource (even if it's a duplicate)
      assertThat(canTransformResource("multiple-contents", file3)).isFalse()

      assertThat(canTransformResource("single-source", file1)).isFalse()

      assertThat(canTransformResource("same-content-twice", file1)).isFalse()
      assertThat(canTransformResource("same-content-twice", file2)).isTrue()

      assertThat(canTransformResource("differing-content-2", file1)).isFalse()
      assertThat(canTransformResource("differing-content-2", file3)).isFalse()

      assertThat(sources.keys).containsExactlyInAnyOrder(
        "multiple-contents",
        "single-source",
        "same-content-twice",
        "differing-content-2",
      )

      val pathInfosMultipleContents = sources.getValue("multiple-contents")
      assertThat(pathInfosMultipleContents.failOnDuplicateContent).isEqualTo(exclusionCheck)
      assertThat(pathInfosMultipleContents.uniqueContentCount()).isEqualTo(2)
      assertThat(pathInfosMultipleContents.filesPerHash).containsOnly(
        hash1 to listOf(file1, file2),
        hash3 to listOf(file3),
      )

      val pathInfosSingleSource = sources.getValue("single-source")
      assertThat(pathInfosSingleSource.failOnDuplicateContent).isTrue()
      assertThat(pathInfosSingleSource.uniqueContentCount()).isEqualTo(1)
      assertThat(pathInfosSingleSource.filesPerHash).containsOnly(hash1 to listOf(file1))

      val pathInfosSameContentTwice = sources.getValue("same-content-twice")
      assertThat(pathInfosSameContentTwice.failOnDuplicateContent).isTrue()
      assertThat(pathInfosSameContentTwice.uniqueContentCount()).isEqualTo(1)
      assertThat(pathInfosSameContentTwice.filesPerHash).containsOnly(hash1 to listOf(file1, file2))

      val pathInfosDifferingContent2 = sources.getValue("differing-content-2")
      assertThat(pathInfosDifferingContent2.failOnDuplicateContent).isTrue()
      assertThat(pathInfosDifferingContent2.uniqueContentCount()).isEqualTo(2)
      assertThat(pathInfosDifferingContent2.filesPerHash).containsOnly(hash1 to listOf(file1), hash3 to listOf(file3))

      if (exclusionCheck) {
        assertThat(duplicateContentViolations()).containsOnly(
          "multiple-contents" to pathInfosMultipleContents,
          "differing-content-2" to pathInfosDifferingContent2,
        )
      } else {
        assertThat(duplicateContentViolations()).containsOnly("differing-content-2" to pathInfosDifferingContent2)
      }
    }
  }
}
