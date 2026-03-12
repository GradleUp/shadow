package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import java.util.regex.Pattern

/** https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html */
private val classPattern: Pattern = Pattern.compile("([\\[()BCDFIJSZ]*)?L([^;]+);?")

internal fun Set<Relocator>.mapName(
  name: String,
  mapLiterals: Boolean = false,
  onModified: () -> Unit,
): String {
  // Maybe a list of types.
  val newName = name.split(';').joinToString(";") { realMap(it, mapLiterals) }
  if (newName != name) {
    onModified()
  }
  return newName
}

private fun Set<Relocator>.realMap(name: String, mapLiterals: Boolean): String {
  var newName = name
  var prefix = ""
  var suffix = ""

  val matcher = classPattern.matcher(newName)
  if (matcher.matches()) {
    prefix = matcher.group(1) + "L"
    suffix = ""
    newName = matcher.group(2)
  }

  for (relocator in this) {
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
