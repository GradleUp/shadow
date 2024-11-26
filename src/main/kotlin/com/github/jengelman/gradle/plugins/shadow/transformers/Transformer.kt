package com.github.jengelman.gradle.plugins.shadow.transformers

import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Named
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Internal

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ResourceTransformer.java).
 *
 * @author Jason van Zyl
 * @author Charlie Knudsen
 * @author John Engelman
 */
@JvmDefaultWithCompatibility
public interface Transformer : Named {
  public fun canTransformResource(element: FileTreeElement): Boolean

  public fun transform(context: TransformerContext)

  public fun hasTransformedResource(): Boolean

  public fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean)

  @Internal
  override fun getName(): String = this::class.java.simpleName
}

public object NoOpTransformer : Transformer {
  public override fun canTransformResource(element: FileTreeElement): Boolean = false
  public override fun transform(context: TransformerContext): Unit = Unit
  public override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean): Unit = Unit
  public override fun hasTransformedResource(): Boolean = false
}
