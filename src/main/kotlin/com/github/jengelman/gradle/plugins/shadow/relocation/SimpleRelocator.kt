package com.github.jengelman.gradle.plugins.shadow.relocation

import java.util.regex.Pattern
import org.codehaus.plexus.util.SelectorUtils
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Modified from org.apache.maven.plugins.shade.relocation.SimpleRelocator.java
 *
 * @author Jason van Zyl
 * @author Mauro Talevi
 * @author John Engelman
 */
@CacheableRelocator
public class SimpleRelocator @JvmOverloads constructor(
    patt: String?,
    shadedPattern: String?,
    includes: List<String>?,
    excludes: List<String>?,
    private val rawString: Boolean = false,
) : Relocator {

    @Input
    @Optional
    public val pattern: String?

    @Input
    @Optional
    public val pathPattern: String

    @Input
    @Optional
    public val shadedPattern: String?

    @Input
    @Optional
    public val shadedPathPattern: String

    @Input
    @Optional
    public val includes: MutableSet<String> = mutableSetOf()

    @Input
    @Optional
    public val excludes: MutableSet<String> = mutableSetOf()

    init {
        if (rawString) {
            this.pathPattern = patt.orEmpty()
            this.shadedPathPattern = shadedPattern.orEmpty()
            this.pattern = null
            this.shadedPattern = null
        } else {
            this.pattern = patt?.replace('/', '.').orEmpty()
            this.pathPattern = patt?.replace('.', '/').orEmpty()
            this.shadedPattern = shadedPattern?.replace('/', '.') ?: "hidden.$pattern"
            this.shadedPathPattern = shadedPattern?.replace('.', '/') ?: "hidden/$pathPattern"
        }
        this.includes += normalizePatterns(includes)
        this.excludes += normalizePatterns(excludes)
    }

    public fun include(pattern: String): SimpleRelocator = apply {
        includes.addAll(normalizePatterns(listOf(pattern)))
    }

    public fun exclude(pattern: String): SimpleRelocator = apply {
        excludes.addAll(normalizePatterns(listOf(pattern)))
    }

    override fun canRelocatePath(path: String): Boolean {
        if (rawString) {
            return Pattern.compile(pathPattern).matcher(path).find()
        }
        if (path.length < pathPattern.length) {
            return false
        }
        var modifiedPath = path
        if (path.endsWith(".class")) {
            if (path.length == 6) {
                return false
            }
            modifiedPath = path.substring(0, path.length - 6)
        }
        val pathStartsWithPattern = if (modifiedPath[0] == '/') {
            modifiedPath.startsWith(pathPattern, 1)
        } else {
            modifiedPath.startsWith(pathPattern)
        }
        return pathStartsWithPattern &&
            includes.isMatching(modifiedPath, true) &&
            !excludes.isMatching(modifiedPath, false)
    }

    override fun canRelocateClass(className: String): Boolean {
        return !rawString && className.indexOf('/') < 0 && canRelocatePath(className.replace('.', '/'))
    }

    override fun relocatePath(context: RelocatePathContext): String {
        val path = context.path
        context.stats.relocate(pathPattern, shadedPathPattern)
        return if (rawString) {
            path.replace(pathPattern.toRegex(), shadedPathPattern)
        } else {
            path.replaceFirst(pathPattern, shadedPathPattern)
        }
    }

    override fun relocateClass(context: RelocateClassContext): String {
        val clazz = context.className
        context.stats.relocate(pathPattern, shadedPathPattern)
        return clazz.replaceFirst(pattern!!.toRegex(), shadedPattern!!)
    }

    override fun applyToSourceContent(sourceContent: String): String {
        return if (rawString) {
            sourceContent
        } else {
            sourceContent.replace("\\b$pattern".toRegex(), shadedPattern!!)
        }
    }

    private fun normalizePatterns(patterns: Collection<String>?): Set<String> {
        val normalized = mutableSetOf<String>()
        patterns?.forEach { pattern ->
            if (pattern.startsWith(SelectorUtils.REGEX_HANDLER_PREFIX)) {
                normalized.add(pattern)
            } else {
                val classPattern = pattern.replace('.', '/')
                normalized.add(classPattern)
                if (classPattern.endsWith("/*")) {
                    val packagePattern = classPattern.substring(0, classPattern.lastIndexOf('/'))
                    normalized.add(packagePattern)
                }
            }
        }
        return normalized
    }

    private fun Set<String>.isMatching(path: String, default: Boolean): Boolean {
        if (isNotEmpty()) {
            forEach { pattern ->
                if (SelectorUtils.matchPath(pattern, path, "/", true)) return true
            }
            return false
        }
        return default
    }
}
