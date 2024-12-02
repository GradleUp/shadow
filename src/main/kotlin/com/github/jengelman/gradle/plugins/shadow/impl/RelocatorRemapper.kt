package com.github.jengelman.gradle.plugins.shadow.impl

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
public open class RelocatorRemapper(
  private val relocators: List<Relocator>,
  private val stats: ShadowStats,
) : Remapper() {
  private val classPattern: Pattern = Pattern.compile("(\\[*)?L(.+)")

  public var currentFilePath: String? = null

  public open fun hasRelocators(): Boolean = relocators.isNotEmpty()

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
      if (!this.canRelocateSourceFile(relocator)) {
        continue
      }

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

  public open fun mapPath(path: String): String {
    return map(path.substring(0, path.indexOf('.')))
  }

  public open fun mapPath(path: ShadowCopyAction.RelativeArchivePath): String {
    return mapPath(path.pathString)
  }

  private fun canRelocateSourceFile(relocator: Relocator): Boolean {
    val currentFilePath: String? = this.currentFilePath
    return currentFilePath == null || relocator.canRelocateSourceFile(currentFilePath)
  }
}
