package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import java.lang.reflect.Field
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

internal class RelocationClassWriter(
  classReader: ClassReader,
  relocators: Set<Relocator>,
  flags: Int = 0,
) : ClassWriter(classReader, flags) {
  internal var isRelocated = false

  init {
    // If the class is a Kotlin class, we need to apply relocations to the symbol table.
    if (classReader.isKotlinClass()) {
      val symbolTable = symbolTableField.get(this)

      @Suppress("UNCHECKED_CAST")
      val entries = entriesField.get(symbolTable) as Array<Any?>
      entries.forEach { entryObj ->
        if (entryObj != null) {
          (symbolValueField.get(entryObj) as? String)?.let { value ->
            val newValue = relocators.relocateClass(value)
            if (value != newValue) {
              symbolValueField.set(entryObj, newValue)
              isRelocated = true
            }
          }
        }
      }
    }
  }

  private fun ClassReader.isKotlinClass(): Boolean {
    var flag = false
    accept(
      object : ClassVisitor(Opcodes.ASM9) {
        override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
          if (desc == "Lkotlin/Metadata;") {
            flag = true
          }
          return super.visitAnnotation(desc, visible)
        }
      },
      ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
    )
    return flag
  }

  companion object Companion {
    private val classWriterClass = ClassWriter::class.java
    private val symbolTableClass: Class<*> = Class.forName("org.objectweb.asm.SymbolTable") // Package private.
    private val symbolClass: Class<*> = Class.forName("org.objectweb.asm.Symbol") // Package private.

    private val symbolTableField: Field = classWriterClass.getDeclaredField("symbolTable")
      .apply { isAccessible = true }
    private val entriesField: Field = symbolTableClass.getDeclaredField("entries")
      .apply { isAccessible = true }
    private val symbolValueField: Field = symbolClass.getDeclaredField("value")
      .apply { isAccessible = true }
  }
}
