package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

/**
 * This is used for tests only as the [Transformer] subclasses declared in test build scripts can't be accessed due to
 * something like `java.lang.NoClassDefFoundError: com/github/jengelman/gradle/plugins/shadow/transformers/Transformer`,
 * it looks like a bug of Gradle when CC is enabled.
 */
internal object CustomTransformer : Transformer {
  override fun canTransformResource(element: FileTreeElement): Boolean = true
  override fun transform(context: TransformerContext): Unit = Unit
  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean): Unit = Unit
  override fun hasTransformedResource(): Boolean = true
}
