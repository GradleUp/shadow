package com.github.jengelman.gradle.plugins.shadow.relocation

import java.util.regex.Pattern
import org.codehaus.plexus.util.SelectorUtils
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
open class SimpleRelocator @JvmOverloads constructor(
    pattern: String?,
    shadedPattern: String?,
    includes: List<String>?,
    excludes: List<String>?,
    private val _isRawString: Boolean = false,
) : Relocator {
    private val _pattern: String?
    private val _pathPattern: String
    private val _shadedPattern: String?
    private val _shadedPathPattern: String
    private val _includes = mutableSetOf<String>()
    private val _excludes = mutableSetOf<String>()

    init {
        if (_isRawString) {
            _pathPattern = pattern.orEmpty()
            _shadedPathPattern = shadedPattern.orEmpty()
            _pattern = null // not used for raw string relocator
            _shadedPattern = null // not used for raw string relocator
        } else {
            if (pattern == null) {
                _pattern = ""
                _pathPattern = ""
            } else {
                _pattern = pattern.replace('/', '.')
                _pathPattern = pattern.replace('.', '/')
            }
            if (shadedPattern == null) {
                _shadedPattern = "hidden.${_pattern}"
                _shadedPathPattern = "hidden/$_pathPattern"
            } else {
                _shadedPattern = shadedPattern.replace('/', '.')
                _shadedPathPattern = shadedPattern.replace('.', '/')
            }
        }
        _includes += normalizePatterns(includes)
        _excludes += normalizePatterns(excludes)
    }

    @get:Input
    @get:Optional
    open val pattern: String? get() = _pattern

    @get:Input
    open val pathPattern: String get() = _pathPattern

    @get:Input
    @get:Optional
    open val shadedPattern: String? get() = _shadedPattern

    @get:Input
    open val shadedPathPattern: String get() = _shadedPathPattern

    @get:Input
    open val isRawString: Boolean get() = _isRawString

    @get:Input
    open val includes: Set<String> get() = _includes

    @get:Input
    open val excludes: Set<String> get() = _excludes

    open fun include(pattern: String): SimpleRelocator = apply {
        _includes += normalizePatterns(listOf(pattern))
    }

    open fun exclude(pattern: String): SimpleRelocator = apply {
        _excludes += normalizePatterns(listOf(pattern))
    }

    override fun canRelocatePath(path: String): Boolean {
        var adjustPath = path
        if (_isRawString) {
            return Pattern.compile(_pathPattern).matcher(adjustPath).find()
        }

        // If string is too short - no need to perform expensive string operations
        if (adjustPath.length < _pathPattern.length) {
            return false
        }

        if (adjustPath.endsWith(".class")) {
            // Safeguard against strings containing only ".class"
            if (adjustPath.length == 6) {
                return false
            }
            adjustPath = adjustPath.substring(0, adjustPath.length - 6)
        }

        // Allow for annoying option of an extra / on the front of a path. See MSHADE-119 comes from getClass().getResource("/a/b/c.properties").
        val pathStartsWithPattern = if (adjustPath.startsWith("/")) {
            adjustPath.startsWith(_pathPattern, 1)
        } else {
            adjustPath.startsWith(_pathPattern)
        }

        return if (pathStartsWithPattern) isIncluded(adjustPath) && !isExcluded(adjustPath) else false
    }

    override fun canRelocateClass(className: String): Boolean {
        return !_isRawString && !className.contains('/') && canRelocatePath(className.replace('.', '/'))
    }

    override fun relocatePath(context: RelocatePathContext): String {
        val path = context.path
        context.stats.relocate(_pathPattern, _shadedPathPattern)
        return if (_isRawString) {
            path.replace(_pathPattern.toRegex(), _shadedPathPattern)
        } else {
            path.replaceFirst(_pathPattern, _shadedPathPattern)
        }
    }

    override fun relocateClass(context: RelocateClassContext): String {
        val clazz = context.className
        context.stats.relocate(_pathPattern, _shadedPathPattern)
        return clazz.replaceFirst(_pattern.orEmpty(), _shadedPattern.orEmpty())
    }

    override fun applyToSourceContent(sourceContent: String): String {
        return if (_isRawString) {
            sourceContent
        } else {
            sourceContent.replace("\\b$_pattern".toRegex(), _shadedPattern.orEmpty())
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
        return _includes.isEmpty() || _includes.any { SelectorUtils.matchPath(it, path, "/", true) }
    }

    private fun isExcluded(path: String): Boolean {
        return _excludes.any { SelectorUtils.matchPath(it, path, "/", true) }
    }
}
