package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.relocation.CacheableRelocator
import java.io.IOException
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Named
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ResourceTransformer.java).
 *
 * @author Jason van Zyl
 * @author Charlie Knudsen
 * @author John Engelman
 */
@JvmDefaultWithCompatibility
public interface ResourceTransformer : Named {
  public fun canTransformResource(element: FileTreeElement): Boolean

  @Throws(IOException::class)
  public fun transform(context: TransformerContext)

  public fun hasTransformedResource(): Boolean

  @Throws(IOException::class)
  public fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean)

  @Internal
  override fun getName(): String = this::class.java.simpleName

  /**
   * This is used for creating Gradle's lazy properties in the subclass, Shadow's build-in transformers that depend on
   * this have been injected via [ObjectFactory.newInstance]. Custom transformers should implement or inject
   * this property if they need to access it.
   */
  @get:Internal
  public val objectFactory: ObjectFactory
    get() = throw NotImplementedError("You have to make sure this has been implemented or injected.")

  /**
   * This also implements [ResourceTransformer] but no-op, which means it could be used by Kotlin delegations.
   */
  public companion object : ResourceTransformer {
    @JvmStatic
    public fun <T : ResourceTransformer> Class<T>.create(objectFactory: ObjectFactory): T {
      // If the constructor takes a single ObjectFactory, inject it in.
      val constructor = constructors.find {
        it.parameterTypes.singleOrNull() == ObjectFactory::class.java
      }
      return if (constructor != null) {
        objectFactory.newInstance(this@create)
      } else {
        getDeclaredConstructor().newInstance()
      }
    }

    public override fun canTransformResource(element: FileTreeElement): Boolean = false
    public override fun transform(context: TransformerContext): Unit = Unit
    public override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean): Unit = Unit
    public override fun hasTransformedResource(): Boolean = false
  }
}

/**
 * Marks that a given instance of [ResourceTransformer] is compatible with the Gradle build cache.
 * In other words, it has its appropriate inputs annotated so that Gradle can consider them when
 * determining the cache key.
 *
 * @see [CacheableRelocator]
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class CacheableTransformer
