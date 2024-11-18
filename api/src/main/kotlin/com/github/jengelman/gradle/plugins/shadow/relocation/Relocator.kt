package com.github.jengelman.gradle.plugins.shadow.relocation

/**
 * Modified from `org.apache.maven.plugins.shade.relocation.Relocator.java`
 *
 * @author Jason van Zyl
 * @author John Engelman
 */
interface Relocator {
    fun canRelocatePath(path: String): Boolean

    fun relocatePath(context: RelocatePathContext): String

    fun canRelocateClass(className: String): Boolean

    fun relocateClass(context: RelocateClassContext): String

    fun applyToSourceContent(sourceContent: String): String

    companion object {
        val ROLE: String = Relocator::class.java.name
    }
}
