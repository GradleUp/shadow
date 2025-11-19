package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.LinkedHashSet
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.util.PatternSet

/**
 * Generates a license file using the configured license text source.
 *
 * A mandatory `SPDX-License-Identifier` is placed in front of the license text to avoid ambiguous
 * license detection by license-detection-tools.
 *
 * License texts found in the files names:
 *
 * - `META-INF/LICENSE`
 * - `META-INF/LICENSE.txt`
 * - `META-INF/LICENSE.md`
 * - `LICENSE`
 * - `LICENSE.txt`
 * - `LICENSE.md`
 *
 * are included for the shadowed jar sources.
 *
 * To exclude these defaults, add [exclude]s to the transformer configuration.
 *
 * Use the [org.gradle.api.tasks.util.PatternFilterable] functions to specify a different set of files to include,
 * the paths mentioned above are then not considered unless explicitly included.
 */
@CacheableTransformer
public open class MergeLicenseResourceTransformer(
  objectFactory: ObjectFactory,
  patternSet: PatternSet,
) : PatternFilterableResourceTransformer(patternSet) {
  @get:Internal
  internal val elements: MutableSet<String> = LinkedHashSet()

  /** Path to write the aggregated license file to. Defaults to `META-INF/LICENSE`. */
  @get:Input
  public val outputPath: Property<String> = objectFactory.property("META-INF/LICENSE")

  /**
   * The generated license file is potentially a collection of multiple license texts. To avoid
   * ambiguous license detection by license-detection-tools, an SPDX license identifier header
   * (`SPDX-License-Identifier:`) is added at the beginning of the generated file if the value of
   * this property is present and not empty. Defaults to `Apache-2.0`.
   */
  @get:Input
  public val artifactLicenseSpdxId: Property<String> = objectFactory.property("Apache-2.0")

  /** Path to the project's license text, this property *must* be configured. */
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public val artifactLicense: RegularFileProperty = objectFactory.fileProperty()

  /**
   * Separator between the project's license text and license texts from the included dependencies.
   */
  @get:Input
  public val firstSeparator: Property<String> =
    objectFactory
      .property(
        """

        ${"-".repeat(120)}

        This artifact includes dependencies with the following licenses:
        ----------------------------------------------------------------

        """
          .trimIndent(),
      )

  /** Separator between included dependency license texts. */
  @get:Input
  public val separator: Property<String> = objectFactory.property("\n${"-".repeat(120)}\n")

  @Inject
  public constructor(objectFactory: ObjectFactory) : this(
    objectFactory,
    patternSet = PatternSet().apply {
      include(
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/LICENSE.md",
        "LICENSE",
        "LICENSE.txt",
        "LICENSE.md",
      )
    },
  )

  override fun transform(context: TransformerContext) {
    transformInternal(context.inputStream.readAllBytes())
  }

  override fun hasTransformedResource(): Boolean = true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    os.putNextEntry(zipEntry(outputPath.get()))
    os.write(buildLicenseFile())
    os.closeEntry()
  }

  internal fun transformInternal(bytes: ByteArray) {
    val content = bytes.toString(UTF_8).trim('\n', '\r')
    if (!content.isEmpty()) {
      elements.add(content)
    }
  }

  internal fun buildLicenseFile(): ByteArray {
    val buffer = ByteArrayOutputStream()

    val spdxId = artifactLicenseSpdxId.orElse("").get().trim()
    if (spdxId.isNotBlank()) {
      buffer.write("SPDX-License-Identifier: $spdxId\n".toByteArray())
    }

    artifactLicense.get().asFile.inputStream().use { it.copyTo(buffer) }

    if (elements.isNotEmpty()) {
      buffer.write(("\n" + firstSeparator.get() + "\n").toByteArray())

      var first = true
      val separatorBytes = ("\n" + separator.get() + "\n").toByteArray()
      for (element in elements) {
        if (!first) {
          buffer.write(separatorBytes)
        }
        buffer.write(element.toByteArray())
        first = false
      }
    }

    return buffer.toByteArray()
  }
}
