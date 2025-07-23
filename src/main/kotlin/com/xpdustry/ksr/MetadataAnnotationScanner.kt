/*
 * This file is part of KSR, a gradle plugin for handling Kotlin metadata relocation.
 *
 * MIT License
 *
 * Copyright (c) 2025 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.ksr

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

internal class MetadataAnnotationScanner(
  private val cw: ClassWriter,
  private val relocators: List<KotlinRelocator>,
) : ClassVisitor(Opcodes.ASM9, cw) {
  internal var wasRelocated = false

  override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = if (descriptor == "Lkotlin/Metadata;") {
    MetadataVisitor(cw.visitAnnotation(descriptor, visible))
  } else {
    cw.visitAnnotation(descriptor, visible)
  }

  inner class MetadataVisitor(av: AnnotationVisitor, private val thatArray: Boolean = false) : AnnotationVisitor(Opcodes.ASM9, av) {
    override fun visit(name: String?, value: Any) {
      val newValue =
        when {
          thatArray && value is String && value.startsWith("(") -> {
            relocators.applyPathRelocation(value).also {
              if (it != value) wasRelocated = true
            }
          }
          else -> value
        }
      av.visit(name, newValue)
    }

    override fun visitArray(name: String): AnnotationVisitor = if (name == "d2") MetadataVisitor(av.visitArray(name), true) else av.visitArray(name)
  }
}
