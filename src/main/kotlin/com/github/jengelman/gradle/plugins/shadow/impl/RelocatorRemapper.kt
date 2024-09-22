
package com.github.jengelman.gradle.plugins.shadow.impl

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.util.regex.Pattern
import org.objectweb.asm.commons.Remapper

/**
 * Modified from org.apache.maven.plugins.shade.DefaultShader.java#RelocatorRemapper
 *
 * @author John Engelman
 */
class RelocatorRemapper(
  internal val relocators: List<Relocator>,
  internal val stats: ShadowStats,
) : Remapper() {
  private val classPattern: Pattern = Pattern.compile("(\\[*)?L(.+)")

  fun hasRelocators(): Boolean = relocators.isNotEmpty()

  override fun mapValue(value: Any): Any {
    if (value is String) {
      return mapStringValue(value, true)
    }
    return super.mapValue(value)
  }

  override fun map(internalName: String): String {
    return mapStringValue(internalName, false)
  }

  fun mapPath(path: String): String {
    return map(path.substring(0, path.indexOf(".")))
  }

  fun mapPath(path: ShadowCopyAction.RelativeArchivePath): String {
    return mapPath(path.pathString)
  }

  private fun mapStringValue(value: String, relocatePath: Boolean): String {
    var name = value
    var finalValue = name

    var prefix = ""
    var suffix = ""

    val m = classPattern.matcher(name)
    if (m.matches()) {
      prefix = m.group(1) + "L"
      suffix = ""
      name = m.group(2)
    }

    for (r in relocators) {
      if (r.canRelocateClass(name)) {
        val classContext = RelocateClassContext(name, stats)
        finalValue = prefix + r.relocateClass(classContext) + suffix
        break
      } else if (r.canRelocatePath(name)) {
        if (!relocatePath) continue
        val pathContext = RelocatePathContext(name, stats)
        finalValue = prefix + r.relocatePath(pathContext) + suffix
        break
      }
    }
    return finalValue
  }
}
