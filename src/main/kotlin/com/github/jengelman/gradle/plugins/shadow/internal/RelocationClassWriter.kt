package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
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
  isKotlinClass: Boolean = hasKotlinMetadataAnnotation(classReader),
) : ClassWriter(classReader, flags) {
  internal var isRelocated = false

  init {
    // If the class is a Kotlin class, we need to apply relocations to the symbol table.
    if (isKotlinClass) {
      val symbolTable = symbolTableField.get(this)

      @Suppress("UNCHECKED_CAST")
      val entries = entriesField.get(symbolTable) as Array<Any?>
      entries.forEach { entryObj ->
        if (entryObj != null) {
          (symbolValueField.get(entryObj) as? String)?.let { value ->
            val newValue = relocators.applyClassRelocation(value)
            if (value != newValue) {
              symbolValueField.set(entryObj, newValue)
              isRelocated = true
            }
          }
        }
      }
    }
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

    /**
     * Efficiently checks if the class file contains the kotlin.Metadata annotation
     * by scanning the constant pool for the annotation descriptor.
     */
    private fun hasKotlinMetadataAnnotation(classReader: ClassReader): Boolean {
      val bytes = classReader.b
      // Constant pool count is at byte 8 (u2)
      val cpCount = ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
      var i = 10
      for (cpIndex in 1 until cpCount) {
        val tag = bytes[i].toInt() and 0xFF
        when (tag) {
          1 -> { // CONSTANT_Utf8
            val length = ((bytes[i + 1].toInt() and 0xFF) shl 8) or (bytes[i + 2].toInt() and 0xFF)
            val str = String(bytes, i + 3, length, Charsets.UTF_8)
            if (str == "Lkotlin/Metadata;") {
              return true
            }
            i += 3 + length
          }
          3, 4 -> i += 5 // Integer, Float
          5, 6 -> i += 9 // Long, Double
          7, 8, 16 -> i += 3 // Class, String, MethodType
          15 -> i += 4 // MethodHandle
          18 -> i += 5 // InvokeDynamic
          9, 10, 11, 12 -> i += 5 // Field/Method/InterfaceMethod/NameAndType
          else -> return false // Unknown tag, invalid class file
        }
        // Long and Double take up two entries in the constant pool
        if (tag == 5 || tag == 6) {
          cpIndex.inc()
        }
      }
      return false
    }

    fun Iterable<Relocator>.applyClassRelocation(value: String): String = fold(value) { string, relocator ->
      relocator.relocateClass(RelocateClassContext(string))
    }
  }
}
