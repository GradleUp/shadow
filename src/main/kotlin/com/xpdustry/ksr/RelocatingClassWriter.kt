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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

internal class RelocatingClassWriter(
  reader: ClassReader,
  flags: Int,
  relocators: List<KotlinRelocator>,
) : ClassWriter(reader, flags) {

  internal var wasRelocated = false

  init {
    val symbolTable = symbolTableField.get(this)

    @Suppress("UNCHECKED_CAST")
    val entries = entriesField.get(symbolTable) as Array<Any?>
    entries.forEach { entryObj ->
      if (entryObj != null) {
        (symbolValueField.get(entryObj) as? String)?.let { value ->
          val newValue = relocators.applyPathRelocation(value)
          if (value != newValue) {
            symbolValueField.set(entryObj, newValue)
            wasRelocated = true
          }
        }
      }
    }
  }

  companion object {
    private val classWriterClass = Class.forName("org.objectweb.asm.ClassWriter")
    private val symbolTableClass = Class.forName("org.objectweb.asm.SymbolTable")
    private val symbolTableField =
      classWriterClass.getDeclaredField("symbolTable").apply { isAccessible = true }
    private val entriesField =
      symbolTableClass.getDeclaredField("entries").apply { isAccessible = true }
    private val symbolClass = Class.forName("org.objectweb.asm.Symbol")
    private val symbolValueField =
      symbolClass.getDeclaredField("value").apply { isAccessible = true }
  }
}
