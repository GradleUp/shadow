package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
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
) : Remapper() {
  private val classPattern: Pattern = Pattern.compile("(\\[*)?L(.+)")

  override fun mapValue(value: Any): Any {
    return if (value is String) {
      mapName(value, mapLiterals = true)
    } else {
      super.mapValue(value)
    }
  }

  override fun map(internalName: String): String = mapName(internalName)

  fun mapPath(path: String): String {
    return path.substringBefore('.')
  }

  private fun mapName(name: String, mapLiterals: Boolean = false): String {
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
      if (mapLiterals && relocator.skipStringConstants) {
        return name
      } else if (relocator.canRelocateClass(newName)) {
        return prefix + relocator.relocateClass(newName) + suffix
      } else if (relocator.canRelocatePath(newName)) {
        return prefix + relocator.relocatePath(newName) + suffix
      }
    }

    return name
  }
}
