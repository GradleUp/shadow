package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MergeLicenseResourceTransformerTest : BaseTransformerTest<MergeLicenseResourceTransformer>() {
  @Test
  fun defaultIncludes() = with(transformer) {
    assertThat(canTransformResource("META-INF/LICENSE")).isTrue()
    assertThat(canTransformResource("META-INF/LICENSE.txt")).isTrue()
    assertThat(canTransformResource("META-INF/LICENSE.md")).isTrue()
    assertThat(canTransformResource("LICENSE")).isTrue()
    assertThat(canTransformResource("LICENSE.txt")).isTrue()
    assertThat(canTransformResource("LICENSE.md")).isTrue()
    assertThat(canTransformResource("something else")).isFalse()
  }

  @Test
  fun customIncludes() = with(transformer) {
    include("META-INF/FOO")
    exclude("META-INF/LICENSE*")
    exclude("LICENSE*")
    assertThat(canTransformResource("META-INF/FOO")).isTrue()
    assertThat(canTransformResource("META-INF/LICENSE")).isFalse()
    assertThat(canTransformResource("META-INF/LICENSE.txt")).isFalse()
    assertThat(canTransformResource("META-INF/LICENSE.md")).isFalse()
    assertThat(canTransformResource("LICENSE")).isFalse()
    assertThat(canTransformResource("LICENSE.txt")).isFalse()
    assertThat(canTransformResource("LICENSE.md")).isFalse()
    assertThat(canTransformResource("something else")).isFalse()
  }

  @Test
  fun deduplicateLicenseTexts(@TempDir tempDir: Path) = with(transformer) {
    transformInternal("license one".toByteArray())
    transformInternal("\r\nlicense one\r\n".toByteArray())
    transformInternal("\nlicense one\n".toByteArray())
    transformInternal("   license two".toByteArray())
    transformInternal("\r\n\n\r\n\n   license two".toByteArray())
    transformInternal("   license two\r\n\n\r\n\n".toByteArray())
    transformInternal("license three".toByteArray())

    val artifactLicenseFile = tempDir.resolve("artifact-license").toFile()
    artifactLicenseFile.writeText("artifact license file content")
    artifactLicense.set(artifactLicenseFile)

    assertThat(elements).containsExactlyInAnyOrder("license one", "   license two", "license three")

    assertThat(buildLicense()).isEqualTo(
      """
        SPDX-License-Identifier: Apache-2.0
        artifact license file content

        ------------------------------------------------------------------------------------------------------------------------

        This artifact includes dependencies with the following licenses:
        ----------------------------------------------------------------

        license one

        ------------------------------------------------------------------------------------------------------------------------

           license two

        ------------------------------------------------------------------------------------------------------------------------

        license three
      """.trimIndent(),
    )
  }

  @Test
  fun singleAdditionalLicense(@TempDir tempDir: Path) = with(transformer) {
    transformInternal("license one".toByteArray())

    val artifactLicenseFile = tempDir.resolve("artifact-license").toFile()
    artifactLicenseFile.writeText("artifact license file content")
    artifactLicense.set(artifactLicenseFile)

    assertThat(elements).containsExactlyInAnyOrder("license one")

    assertThat(buildLicense()).isEqualTo(
      """
        SPDX-License-Identifier: Apache-2.0
        artifact license file content

        ------------------------------------------------------------------------------------------------------------------------

        This artifact includes dependencies with the following licenses:
        ----------------------------------------------------------------

        license one
      """.trimIndent(),
    )
  }

  @Test
  fun noAdditionalLicenses(@TempDir tempDir: Path) = with(transformer) {
    val artifactLicenseFile = tempDir.resolve("artifact-license").toFile()
    artifactLicenseFile.writeText("artifact license file content")
    artifactLicense.set(artifactLicenseFile)

    assertThat(elements).isEmpty()

    assertThat(buildLicense()).isEqualTo(
      """
        SPDX-License-Identifier: Apache-2.0
        artifact license file content
      """.trimIndent(),
    )
  }

  @Test
  fun noSpdxId(@TempDir tempDir: Path) = with(transformer) {
    artifactLicenseSpdxId.unsetConvention()

    val artifactLicenseFile = tempDir.resolve("artifact-license").toFile()
    artifactLicenseFile.writeText("artifact license file content")
    artifactLicense.set(artifactLicenseFile)

    assertThat(elements).isEmpty()

    assertThat(buildLicense()).isEqualTo(
      "artifact license file content",
    )
  }
}
