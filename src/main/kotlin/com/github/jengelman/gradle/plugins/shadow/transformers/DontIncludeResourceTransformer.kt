package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.property
import javax.inject.Inject
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * A resource processor that prevents the inclusion of an arbitrary resource into the shaded JAR.
 *
 * You can also use [com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.exclude] as instead.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.DontIncludeResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/DontIncludeResourceTransformer.java).
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class DontIncludeResourceTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : ResourceTransformer by ResourceTransformer.Companion {
  @get:Optional
  @get:Input
  public open val resource: Property<String> = objectFactory.property()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return !resource.orNull.isNullOrEmpty() && element.path.endsWith(resource.get())
  }
}
