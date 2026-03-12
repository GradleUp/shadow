package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import java.io.File
import org.gradle.api.GradleException
import org.vafer.jdeb.shaded.objectweb.asm.ClassReader
import org.vafer.jdeb.shaded.objectweb.asm.ClassWriter
import org.vafer.jdeb.shaded.objectweb.asm.Opcodes
import org.vafer.jdeb.shaded.objectweb.asm.commons.ClassRemapper
import org.vafer.jdeb.shaded.objectweb.asm.commons.Remapper

/**
 * Applies remapping to the given class with the specified relocation path. The remapped class is
 * then written to the zip file.
 */
internal fun File.remapClass(relocators: Set<Relocator>): ByteArray =
  readBytes().let { bytes ->
    var modified = false
    val remapper = RelocatorRemapper(relocators) { modified = true }

    // We don't pass the ClassReader here. This forces the ClassWriter to rebuild the constant pool.
    // Copying the original constant pool should be avoided because it would keep references to the
    // original class names. This is not a problem at runtime (because these entries in the constant
    // pool are never used), but confuses some tools such as Felix's maven-bundle-plugin that use
    // the constant pool to determine the dependencies of a class.
    val cw = ClassWriter(0)
    val cr = ClassReader(bytes)
    val cv = ClassRemapper(cw, remapper)

    try {
      cr.accept(cv, ClassReader.EXPAND_FRAMES)
    } catch (t: Throwable) {
      throw GradleException("Error in ASM processing class $path", t)
    }

    // If we didn't need to change anything, keep the original bytes as-is.
    if (modified) cw.toByteArray() else bytes
  }

/**
 * Modified from
 * [org.apache.maven.plugins.shade.DefaultShader.RelocatorRemapper](https://github.com/apache/maven-shade-plugin/blob/83c123d1f9c5f6927af2aca12ee322b5168a7c63/src/main/java/org/apache/maven/plugins/shade/DefaultShader.java#L689-L772).
 * Modified from
 * [org.apache.maven.plugins.shade.DefaultShader.DefaultPackageMapper](https://github.com/apache/maven-shade-plugin/blob/199ffaecd26a912527173ed4edae366e48a00998/src/main/java/org/apache/maven/plugins/shade/DefaultShader.java#L737-L774).
 *
 * @author John Engelman
 */
private class RelocatorRemapper(
  private val relocators: Set<Relocator>,
  private val onModified: () -> Unit,
) : Remapper(Opcodes.ASM9) {

  override fun mapValue(value: Any): Any {
    return if (value is String) {
      relocators.mapName(name = value, mapLiterals = true, onModified = onModified)
    } else {
      super.mapValue(value)
    }
  }

  override fun map(internalName: String): String =
    relocators.mapName(name = internalName, onModified = onModified)
}
