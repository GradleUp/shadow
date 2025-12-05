package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.setProperty
import com.github.jengelman.gradle.plugins.shadow.internal.unsafeLazy
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import javax.inject.Inject
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternSet

/**
 * A resource processor that preserves the first resource matched and excludes all others.
 *
 * This is useful when you set `shadowJar.duplicatesStrategy = DuplicatesStrategy.INCLUDE` (the default behavior) and
 * want to ensure that only the first found resource is included in the final JAR. If there are multiple resources with
 * the same path in a project and its dependencies, the first one found should be the project's.
 *
 * This transformer deduplicates included resources based on the path name.
 * See [DeduplicatingResourceTransformer] for a transformer that deduplicates based on the paths and contents of
 * the resources.
 *
 * *Warning* Do **not** combine [DeduplicatingResourceTransformer] with this transformer,
 * as they handle duplicates differently and combining them would lead to redundant or unexpected behavior.
 *
 * @see [DuplicatesStrategy]
 * @see [ShadowJar.getDuplicatesStrategy]
 */
@CacheableTransformer
public open class PreserveFirstFoundResourceTransformer(
  final override val objectFactory: ObjectFactory,
  patternSet: PatternSet,
) : PatternFilterableResourceTransformer(patternSet) {
  private val includeResources by unsafeLazy {
    @Suppress("DEPRECATION")
    include(resources.get())
  }

  @get:Internal
  protected val found: MutableSet<String> = mutableSetOf()

  @get:Deprecated("Use `include(..)` instead")
  @get:Input
  public open val resources: SetProperty<String> = objectFactory.setProperty()

  @Inject
  public constructor(objectFactory: ObjectFactory) : this(objectFactory, PatternSet())

  override fun canTransformResource(element: FileTreeElement): Boolean {
    // Init once before patternSpec is accessed.
    includeResources
    return patternSpec.isSatisfiedBy(element) && !found.add(element.path)
  }
}
