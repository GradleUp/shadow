package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails
import org.vafer.jdeb.shaded.objectweb.asm.ClassReader
import org.vafer.jdeb.shaded.objectweb.asm.ClassWriter
import org.vafer.jdeb.shaded.objectweb.asm.Opcodes
import org.vafer.jdeb.shaded.objectweb.asm.commons.ClassRemapper
import org.vafer.jdeb.shaded.objectweb.asm.commons.Remapper

/**
 * Applies remapping to the given class file using the provided relocators and returns the
 * (possibly) remapped class bytes. If no remapping is required, the original bytes are returned.
 */
internal fun FileCopyDetails.remapClass(relocators: Set<Relocator>): ByteArray =
  file.readBytes().let { bytes ->
    var modified = false
    val remapper = RelocatorRemapper(relocators) { modified = true }

    // We don't pass the ClassReader here. This forces the ClassWriter to rebuild the constant pool.
    // Copying the original constant pool should be avoided because it would keep references to the
    // original class names. This is not a problem at runtime (because these entries in the constant
    // pool are never used), but confuses some tools such as Felix's maven-bundle-plugin that use
    // the constant pool to determine the dependencies of a class.
    try {
      val cw = ClassWriter(0)
      val cr = ClassReader(bytes)
      val cv = ClassRemapper(cw, remapper)
      cr.accept(cv, ClassReader.EXPAND_FRAMES)
      // If we didn't need to change anything, keep the original bytes as-is.
      if (modified) cw.toByteArray() else bytes
    } catch (t: Throwable) {
      throw GradleException("Error in ASM processing class $path", t)
    }
  }

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
