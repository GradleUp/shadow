package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.internal.property
import java.util.regex.Pattern
import org.codehaus.plexus.util.SelectorUtils
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Modified from `org.apache.maven.plugins.shade.relocation.SimpleRelocator.java`
 *
 * @author Jason van Zyl
 * @author Mauro Talevi
 * @author John Engelman
 */
@CacheableRelocator
public open class SimpleRelocator @JvmOverloads constructor(
  objectFactory: ObjectFactory,
  pattern: String?,
  shadedPattern: String?,
  includes: List<String>? = null,
  excludes: List<String>? = null,
  rawString: Boolean = false,
) : Relocator {

  @get:Input
  @get:Optional
  public open val pattern: Property<String> = objectFactory.property()

  @get:Input
  public open val pathPattern: Property<String> = objectFactory.property()

  @get:Input
  @get:Optional
  public open val shadedPattern: Property<String> = objectFactory.property()

  @get:Input
  public open val shadedPathPattern: Property<String> = objectFactory.property()

  @get:Input
  public open val rawString: Property<Boolean> = objectFactory.property()

  @get:Input
  public open val includes: SetProperty<String> = objectFactory.setProperty(String::class.java)

  @get:Input
  public open val excludes: SetProperty<String> = objectFactory.setProperty(String::class.java)

  init {
    this.rawString.set(rawString)
    if (rawString) {
      pathPattern.set(pattern.orEmpty())
      shadedPathPattern.set(shadedPattern.orEmpty())
      // Don't need to assign pattern and shadedPattern for raw string relocator
    } else {
      if (pattern == null) {
        this.pattern.set("")
        this.pathPattern.set("")
      } else {
        this.pattern.set(pattern.replace('/', '.'))
        this.pathPattern.set(pattern.replace('.', '/'))
      }
      if (shadedPattern == null) {
        this.shadedPattern.set(this.pattern.map { "hidden.$it" })
        this.shadedPathPattern.set(this.pathPattern.map { "hidden/$it" })
      } else {
        this.shadedPattern.set(shadedPattern.replace('/', '.'))
        this.shadedPathPattern.set(shadedPattern.replace('.', '/'))
      }
    }
    this.includes.addAll(normalizePatterns(includes))
    this.excludes.addAll(normalizePatterns(excludes))
  }

  public open fun include(pattern: String): SimpleRelocator = apply {
    includes.addAll(normalizePatterns(listOf(pattern)))
  }

  public open fun exclude(pattern: String): SimpleRelocator = apply {
    excludes.addAll(normalizePatterns(listOf(pattern)))
  }

  override fun canRelocatePath(path: String): Boolean {
    if (rawString.get()) return Pattern.compile(pathPattern.get()).matcher(path).find()
    // If string is too short - no need to perform expensive string operations
    if (path.length < pathPattern.get().length) return false
    val adjustedPath = if (path.endsWith(".class")) {
      // Safeguard against strings containing only ".class"
      if (path.length == 6) return false
      path.dropLast(6)
    } else {
      path
    }
    // Allow for annoying option of an extra / on the front of a path. See MSHADE-119 comes from getClass().getResource("/a/b/c.properties").
    val startIndex = if (adjustedPath.startsWith("/")) 1 else 0
    val pathStartsWithPattern = adjustedPath.startsWith(pathPattern.get(), startIndex)
    return pathStartsWithPattern && isIncluded(adjustedPath) && !isExcluded(adjustedPath)
  }

  override fun canRelocateClass(className: String): Boolean {
    return !rawString.get() && !className.contains('/') && canRelocatePath(className.replace('.', '/'))
  }

  override fun relocatePath(context: RelocatePathContext): String {
    val path = context.path
    context.stats.relocate(pathPattern.get(), shadedPathPattern.get())
    return if (rawString.get()) {
      path.replace(pathPattern.get().toRegex(), shadedPathPattern.get())
    } else {
      path.replaceFirst(pathPattern.get(), shadedPathPattern.get())
    }
  }

  override fun relocateClass(context: RelocateClassContext): String {
    context.stats.relocate(pathPattern.get(), shadedPathPattern.get())
    return context.className.replaceFirst(pattern.orNull.orEmpty(), shadedPattern.orNull.orEmpty())
  }

  override fun applyToSourceContent(sourceContent: String): String {
    return if (rawString.get()) {
      sourceContent
    } else {
      sourceContent.replace("\\b${pattern.orNull}".toRegex(), shadedPattern.orNull.orEmpty())
    }
  }

  private fun normalizePatterns(patterns: Collection<String>?) = buildSet {
    for (pattern in patterns.orEmpty()) {
      // Regex patterns don't need to be normalized and stay as is
      if (pattern.startsWith(SelectorUtils.REGEX_HANDLER_PREFIX)) {
        add(pattern)
        continue
      }

      val classPattern = pattern.replace('.', '/')
      add(classPattern)

      if (classPattern.endsWith("/*")) {
        val packagePattern = classPattern.substring(0, classPattern.lastIndexOf('/'))
        add(packagePattern)
      }
    }
  }

  private fun isIncluded(path: String): Boolean {
    return includes.get().isEmpty() || includes.get().any { SelectorUtils.matchPath(it, path, "/", true) }
  }

  private fun isExcluded(path: String): Boolean {
    return excludes.get().any { SelectorUtils.matchPath(it, path, "/", true) }
  }
}
