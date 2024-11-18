package com.github.jengelman.gradle.plugins.shadow.impl

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.util.regex.Pattern
import org.objectweb.asm.commons.Remapper

/**
 * Modified from `org.apache.maven.plugins.shade.DefaultShader.java#RelocatorRemapper`
 *
 * @author John Engelman
 */
open class RelocatorRemapper(
    private val relocators: List<Relocator>,
    private val stats: ShadowStats,
) : Remapper() {
    private val classPattern: Pattern = Pattern.compile("(\\[*)?L(.+)")

    open fun hasRelocators(): Boolean = relocators.isNotEmpty()

    override fun mapValue(value: Any): Any {
        return if (value is String) {
            map(value)
        } else {
            super.mapValue(value)
        }
    }

    override fun map(name: String): String {
        var newName = name
        var prefix = ""
        var suffix = ""

        val matcher = classPattern.matcher(newName)
        if (matcher.matches()) {
            prefix = matcher.group(1) + "L"
            suffix = ""
            newName = matcher.group(2)
        }

        for (relocator in relocators) {
            if (relocator.canRelocateClass(newName)) {
                val classContext = RelocateClassContext.builder().className(newName).stats(stats).build()
                return prefix + relocator.relocateClass(classContext) + suffix
            } else if (relocator.canRelocatePath(newName)) {
                val pathContext = RelocatePathContext.builder().path(newName).stats(stats).build()
                return prefix + relocator.relocatePath(pathContext) + suffix
            }
        }

        return name
    }

    open fun mapPath(path: String): String {
        return map(path.substring(0, path.indexOf('.')))
    }

    open fun mapPath(path: ShadowCopyAction.RelativeArchivePath): String {
        return mapPath(path.pathString)
    }
}
