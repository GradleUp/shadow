package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.unsafeLazy
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.LinkedHashSet
import javax.inject.Inject
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
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
 * License texts found in the files names `META-INF/LICENSE`, `META-INF/LICENSE.txt`,
 * `META-INF/LICENSE.md`, `LICENSE`, `LICENSE.txt`, `LICENSE.md` are included from the shadow jar
 * sources. Use the [PatternFilterable][org.gradle.api.tasks.util.PatternFilterable] functions to
 * specify a different set of files to include, the paths mentioned above are then not considered
 * unless explicitly included.
 */
@Suppress("unused")
@CacheableTransformer
public open class MergeLicenseResourceTransformer(
  objectFactory: ObjectFactory,
  patternSet: PatternSet,
) : PatternFilterableResourceTransformer(patternSet) {
  private val initializePatternSet by unsafeLazy {
    if (patternSet.isEmpty) {
      includeDefaults()
    }
  }

  @get:Internal
  internal val elements: MutableSet<String> = LinkedHashSet()

  public fun includeDefaults(): MergeLicenseResourceTransformer {
    patternSet.include(
      "META-INF/LICENSE",
      "META-INF/LICENSE.txt",
      "META-INF/LICENSE.md",
      "LICENSE",
      "LICENSE.txt",
      "LICENSE.md",
    )
    return this
  }

  /** Path to write the aggregated license file to. Defaults to `META-INF/LICENSE`. */
  @get:Input
  public val outputPath: Property<String> =
    objectFactory.property(String::class.java).value("META-INF/LICENSE")

  /**
   * The generated license file is potentially a collection of multiple license texts. To avoid
   * ambiguous license detection by license-detection-tools, an SPDX license identifier header
   * (`SPDX-License-Identifier:`) is added at the beginning of the generated file if the value of
   * this property is present and not empty. Defaults to `Apache-2.0`.
   */
  @get:Input
  public val artifactLicenseSpdxId: Property<String> =
    objectFactory.property(String::class.java).value("Apache-2.0")

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
      .property(String::class.java)
      .value(
        """

        ${"-".repeat(120)}

        This artifact includes dependencies with the following licenses:
        ----------------------------------------------------------------

        """
          .trimIndent(),
      )

  /** Separator between included dependency license texts. */
  @get:Input
  public val separator: Property<String> =
    objectFactory.property(String::class.java).value("\n${"-".repeat(120)}\n")

  @Inject
  public constructor(objectFactory: ObjectFactory) : this(
    objectFactory,
    patternSet = PatternSet(),
  )

  override fun canTransformResource(element: FileTreeElement): Boolean {
    // Init once before patternSpec is accessed.
    initializePatternSet
    return super.canTransformResource(element)
  }

  override fun transform(context: TransformerContext) {
    transformInternal(context.inputStream.readAllBytes())
  }

  internal fun transformInternal(bytes: ByteArray) {
    val content = bytes.toString(UTF_8).trim('\n', '\r')
    if (!content.isEmpty()) {
      elements.add(content)
    }
  }

  override fun hasTransformedResource(): Boolean = true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    os.putNextEntry(
      ZipEntry(outputPath.get()).apply { time = ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES },
    )

    writeLicenseFile(os)

    os.closeEntry()
  }

  internal fun writeLicenseFile(os: OutputStream) {
    if (artifactLicenseSpdxId.isPresent) {
      val spdxId = artifactLicenseSpdxId.get()
      if (spdxId.isNotBlank()) {
        os.write("SPDX-License-Identifier: $spdxId\n".toByteArray(UTF_8))
      }
    }
    os.write(artifactLicense.get().asFile.readBytes())

    if (!elements.isEmpty()) {
      os.write("\n".toByteArray(UTF_8))
      os.write(firstSeparator.get().toByteArray(UTF_8))
      os.write("\n".toByteArray(UTF_8))

      var first = true
      val separator = (this.separator.get() + "\n").toByteArray(UTF_8)
      for (element in elements) {
        if (!first) {
          os.write("\n".toByteArray(UTF_8))
          os.write(separator)
        }
        os.write(element.toByteArray(UTF_8))
        first = false
      }
    }
  }
}
