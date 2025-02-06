package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.util.regex.Pattern
import org.objectweb.asm.commons.Remapper

/**
 * Modified from [org.apache.maven.plugins.shade.DefaultShader.RelocatorRemapper](https://github.com/apache/maven-shade-plugin/blob/83c123d1f9c5f6927af2aca12ee322b5168a7c63/src/main/java/org/apache/maven/plugins/shade/DefaultShader.java#L689-L772).
 * Modified from [org.apache.maven.plugins.shade.DefaultShader.DefaultPackageMapper](https://github.com/apache/maven-shade-plugin/blob/199ffaecd26a912527173ed4edae366e48a00998/src/main/java/org/apache/maven/plugins/shade/DefaultShader.java#L737-L774).
 *
 * @author John Engelman
 */
internal class RelocatorRemapper(
  private val relocators: Set<Relocator>,
  private val stats: ShadowStats,
) : Remapper() {
  private val classPattern: Pattern = Pattern.compile("(\\[*)?L(.+)")

  fun hasRelocators(): Boolean = relocators.isNotEmpty()

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
        val classContext = RelocateClassContext(className = newName, stats = stats)
        return prefix + relocator.relocateClass(classContext) + suffix
      } else if (relocator.canRelocatePath(newName)) {
        val pathContext = RelocatePathContext(path = newName, stats = stats)
        return prefix + relocator.relocatePath(pathContext) + suffix
      }
    }

    return name
  }

  fun mapPath(path: String): String {
    return map(path.substring(0, path.indexOf('.')))
  }

  fun mapPath(path: ShadowCopyAction.RelativeArchivePath): String {
    return mapPath(path.pathString)
  }
}
