package com.github.jengelman.gradle.plugins.shadow.transformers

import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Named
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Internal

/**
 * Modified from `org.apache.maven.plugins.shade.resource.ResourceTransformer.java`
 *
 * @author Jason van Zyl
 * @author Charlie Knudsen
 * @author John Engelman
 */
public interface Transformer : Named {
  public open fun canTransformResource(element: FileTreeElement): Boolean

  public open fun transform(context: TransformerContext)

  public open fun hasTransformedResource(): Boolean

  public open fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean)

  @Internal
  override fun getName(): String = this::class.java.simpleName
}

public object NoOpTransformer : Transformer {
  public override fun canTransformResource(element: FileTreeElement): Boolean = false
  public override fun transform(context: TransformerContext): Unit = Unit
  public override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean): Unit = Unit
  public override fun hasTransformedResource(): Boolean = false
}
