package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

internal class RelocationClassVisitor(
  private val classWriter: ClassWriter,
  remapper: Remapper,
  private val relocators: Set<Relocator>,
) : ClassRemapper(classWriter, remapper) {

  override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
    val av = classWriter.visitAnnotation(descriptor, visible)
    return if (descriptor == "Lkotlin/Metadata;") {
      KotlinMetadataVisitor(av)
    } else {
      av
    }
  }

  private inner class KotlinMetadataVisitor(
    av: AnnotationVisitor,
    private val thatArray: Boolean = false,
  ) : AnnotationVisitor(Opcodes.ASM9, av) {
    override fun visit(name: String?, value: Any) {
      val newValue = when {
        thatArray && value is String && value.startsWith("(") -> {
          relocators.relocatePath(value)
        }
        else -> value
      }
      av.visit(name, newValue)
    }

    override fun visitArray(name: String): AnnotationVisitor {
      return if (name == "d2") {
        KotlinMetadataVisitor(av.visitArray(name), thatArray = true)
      } else {
        av.visitArray(name)
      }
    }
  }
}
