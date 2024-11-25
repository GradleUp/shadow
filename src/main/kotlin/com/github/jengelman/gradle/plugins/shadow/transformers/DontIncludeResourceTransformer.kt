package com.github.jengelman.gradle.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * A resource processor that prevents the inclusion of an arbitrary resource into the shaded JAR.
 *
 * Modified from [org.apache.maven.plugins.shade.resouce.DontIncludeResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/DontIncludeResourceTransformer.java).
 *
 * @author John Engelman
 */
public open class DontIncludeResourceTransformer : Transformer by NoOpTransformer {
  @get:Optional
  @get:Input
  public var resource: String? = null

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    return !resource.isNullOrEmpty() && path.endsWith(resource!!)
  }
}
