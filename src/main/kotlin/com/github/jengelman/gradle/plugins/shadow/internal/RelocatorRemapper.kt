package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import java.lang.constant.ClassDesc
import java.util.regex.Pattern

/**
 * Modified from
 * [org.apache.maven.plugins.shade.DefaultShader.RelocatorRemapper](https://github.com/apache/maven-shade-plugin/blob/83c123d1f9c5f6927af2aca12ee322b5168a7c63/src/main/java/org/apache/maven/plugins/shade/DefaultShader.java#L689-L772).
 * Modified from
 * [org.apache.maven.plugins.shade.DefaultShader.DefaultPackageMapper](https://github.com/apache/maven-shade-plugin/blob/199ffaecd26a912527173ed4edae366e48a00998/src/main/java/org/apache/maven/plugins/shade/DefaultShader.java#L737-L774).
 *
 * @author John Engelman
 */
internal class RelocatorRemapper(
  private val relocators: Set<Relocator>,
  private val onModified: () -> Unit = {},
) {

  fun map(desc: ClassDesc): ClassDesc {
    val descriptor = desc.descriptorString()
    // We only map class types (L...;), not primitives.
    if (descriptor.length < 3 || descriptor[0] != 'L') return desc

    // Extract internal name: Lcom/example/Foo; -> com/example/Foo
    val internalName = descriptor.substring(1, descriptor.length - 1)
    val newInternalName = mapName(internalName, mapLiterals = false)

    return if (newInternalName != internalName) {
      ClassDesc.ofDescriptor("L$newInternalName;")
    } else {
      desc
    }
  }

  fun mapValue(value: String): String {
    return mapName(value, true)
  }

  private fun mapName(name: String, mapLiterals: Boolean = false): String {
    // Maybe a list of types.
    val newName = name.split(';').joinToString(";") { mapNameImpl(it, mapLiterals) }

    if (newName != name) {
      onModified()
    }
    return newName
  }

  private fun mapNameImpl(name: String, mapLiterals: Boolean): String {
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

  private companion object {
    /** https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html */
    val classPattern: Pattern = Pattern.compile("([\\[()BCDFIJSZ]*)?L([^;]+);?")
  }
}
