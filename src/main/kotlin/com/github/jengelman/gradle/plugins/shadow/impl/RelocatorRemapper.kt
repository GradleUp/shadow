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
public open class RelocatorRemapper(
  private val relocators: List<Relocator>,
  private val stats: ShadowStats,
) : Remapper() {

  private val classPattern: Pattern = Pattern.compile("(\\[*)?L(.+)")

  public open fun hasRelocators(): Boolean = relocators.isNotEmpty()

  override fun mapValue(value: Any): Any {
    return if (value is String) {
      mapName(value, true)
    } else {
      super.mapValue(value)
    }
  }

  override fun map(name: String): String {
    return mapName(name, false)
  }

  public open fun mapPath(path: String): String {
    return map(path.substring(0, path.indexOf('.')))
  }

  public open fun mapPath(path: ShadowCopyAction.RelativeArchivePath): String {
    return mapPath(path.pathString)
  }

  private fun mapName(name: String, relocateClass: Boolean): String {
    var newName = name
    var mappedValue = name

    var prefix = ""
    var suffix = ""

    val matcher = classPattern.matcher(newName)
    if (matcher.matches()) {
      prefix = matcher.group(1) + "L"
      suffix = ""
      newName = matcher.group(2)
    }

    for (relocator in relocators) {
      if (relocator.canRelocateClass(newName) && relocateClass) {
        val classContext = RelocateClassContext.builder().className(newName).stats(stats).build()
        mappedValue = prefix + relocator.relocateClass(classContext) + suffix
        break
      } else if (relocator.canRelocatePath(newName)) {
        val pathContext = RelocatePathContext.builder().path(newName).stats(stats).build()
        mappedValue = prefix + relocator.relocatePath(pathContext) + suffix
        break
      }
    }

    return mappedValue
  }
}
