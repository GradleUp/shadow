package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.setProperty
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import javax.inject.Inject
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * A resource processor that preserves the first resource matched and excludes all others.
 *
 * This is useful when you set `shadowJar.duplicatesStrategy = DuplicatesStrategy.INCLUDE` (the default behavior) and
 * want to ensure that only the first found resource is included in the final JAR. If there are multiple resources with
 * the same path in a project and its dependencies, the first one found should be the projects'.
 *
 * @see [DuplicatesStrategy]
 * @see [ShadowJar.getDuplicatesStrategy]
 */
@CacheableTransformer
public open class PreserveFirstFoundResourceTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : ResourceTransformer by ResourceTransformer.Companion {
  @get:Internal
  protected val found: MutableSet<String> = mutableSetOf()

  @get:Input
  public open val resources: SetProperty<String> = objectFactory.setProperty()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.path
    return resources.get().contains(path) && !found.add(path)
  }
}
