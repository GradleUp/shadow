package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.property
import javax.inject.Inject
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Internal

/**
 * A resource processor that preserves the first resource matched and excludes all others.
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class PreserveFirstFoundResourceTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : ResourceTransformer by ResourceTransformer.Companion {
  @get:Optional
  @get:Input
  public open val resource: Property<String> = objectFactory.property()

  @get:Internal
  protected val found: MutableSet<String> = mutableSetOf()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val resourcePath = resource.orNull
    if (!resourcePath.isNullOrEmpty() && element.path.endsWith(resourcePath)) {
      return !found.add(resourcePath)
    }
    return false
  }
}

