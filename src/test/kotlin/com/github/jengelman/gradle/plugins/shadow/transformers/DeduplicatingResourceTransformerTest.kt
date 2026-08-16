package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import com.github.jengelman.gradle.plugins.shadow.transformers.DeduplicatingResourceTransformer.Companion.sha256Hex
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import de.infix.testBalloon.framework.core.testSuite
import java.io.File
import kotlin.io.path.writeText
import org.gradle.api.GradleException

val DeduplicatingResourceTransformerTests by testSuite {
  runTests(::DeduplicatingResourceTransformerTest)
}

private class DeduplicatingResourceTransformerTest :
  BaseTransformerTest<DeduplicatingResourceTransformer>() {

  private var file1: File
  private var file2: File
  private var file3: File

  private var hash1 = ""
  private var hash3 = ""

  init {
    val content1 = "content1"
    val content2 = "content2"
    file1 = tempDir.resolve("file1").apply { writeText(content1) }.toFile()
    file2 = tempDir.resolve("file2").apply { writeText(content1) }.toFile()
    file3 = tempDir.resolve("file3").apply { writeText(content2) }.toFile()
    hash1 = file1.sha256Hex()
    hash3 = file3.sha256Hex()
  }

  fun sha256Hex() {
    val file = tempDir.resolve("sha256").apply { writeText("content") }

    assertThat(file.toFile().sha256Hex())
      .isEqualTo("ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73")
  }

  fun duplicateContent(exclusionCheck: Boolean) =
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

      assertThat(sources.keys)
        .containsExactlyInAnyOrder(
          "multiple-contents",
          "single-source",
          "same-content-twice",
          "differing-content-2",
        )

      val pathInfosMultipleContents = sources.getValue("multiple-contents")
      assertThat(pathInfosMultipleContents.failOnDuplicateContent).isEqualTo(exclusionCheck)
      assertThat(pathInfosMultipleContents.uniqueContentCount()).isEqualTo(2)
      assertThat(pathInfosMultipleContents.filesPerHash)
        .containsOnly(hash1 to listOf(file1, file2), hash3 to listOf(file3))

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
      assertThat(pathInfosDifferingContent2.filesPerHash)
        .containsOnly(hash1 to listOf(file1), hash3 to listOf(file3))

      if (exclusionCheck) {
        assertThat(duplicateContentViolations())
          .containsOnly(
            "multiple-contents" to pathInfosMultipleContents,
            "differing-content-2" to pathInfosDifferingContent2,
          )
      } else {
        assertThat(duplicateContentViolations())
          .containsOnly("differing-content-2" to pathInfosDifferingContent2)
      }
    }

  fun modifyOutputStreamReportsDuplicateContent() =
    with(transformer) {
      canTransformResource("differing-content", file1)
      canTransformResource("differing-content", file3)

      assertFailure {
          tempJar.zipOutputStream().use {
            modifyOutputStream(it, false)
          }
        }
        .isInstanceOf<GradleException>()
        .hasMessage(
          """
          |Found 1 path duplicate(s) with different content in the shadowed JAR:
          |  * differing-content
          |    * $file1 (SHA256: d0b425e00e15a0d36b9b361f02bab63563aed6cb4665083905386c55d5b679fa)
          |    * $file3 (SHA256: dab741b6289e7dccc1ed42330cae1accc2b755ce8079c2cd5d4b5366c9f769a6)
          |
          """
            .trimMargin()
        )
    }
}
