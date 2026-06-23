package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTreeElement
import org.gradle.api.logging.Logging

internal fun ResourceTransformer.checkDupStrategy(
  canTransformResource: Boolean,
  element: FileTreeElement,
) {
  when {
    !canTransformResource -> return
    element !is FileCopyDetails -> return
    element.duplicatesStrategy == DuplicatesStrategy.EXCLUDE -> {
      val logger = Logging.getLogger(this::class.java)
      logger.warn(
        """
          '${element.path}' is matched by ${this::class.qualifiedName} but its DuplicatesStrategy is ${element.duplicatesStrategy} — duplicates may be silently dropped before the transformer processes them.
          Set it to INCLUDE or WARN to ensure all duplicates are processed by the transformer.
          See https://gradleup.com/shadow/configuration/merging/#handling-duplicates-strategy for more details.
        """
          .trimIndent()
      )
    }
    else -> Unit
  }
}
