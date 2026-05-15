package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import org.gradle.api.file.DuplicatesStrategy.INCLUDE
import org.gradle.api.file.DuplicatesStrategy.WARN
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTreeElement
import org.gradle.api.logging.Logging

internal fun ResourceTransformer.checkDupStrategy(
  canTransformResource: Boolean,
  element: FileTreeElement,
) {
  if (!canTransformResource) return
  if (element !is FileCopyDetails) return
  when (element.duplicatesStrategy) {
    INCLUDE,
    WARN -> Unit

    else -> {
      val logger = Logging.getLogger(this::class.java)
      logger.warn(
        """
          $${element.path}' is matched by ${this::class.qualifiedName} (a merging transformer) but its DuplicatesStrategy is $${element.duplicatesStrategy} — duplicates may be silently dropped before merging.
          Set it to INCLUDE or WARN to ensure all duplicates are processed by the transformer.
          See https://gradleup.com/shadow/configuration/merging/#handling-duplicates-strategy for more details.
        """
          .trimIndent()
      )
    }
  }
}
