/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.jengelman.gradle.plugins.shadow.impl

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.util.regex.Pattern
import org.objectweb.asm.commons.Remapper

/**
 * Modified from `org.apache.maven.plugins.shade.DefaultShader.java#RelocatorRemapper`
 *
 * @author John Engelman
 */
public class RelocatorRemapper(
  private val relocators: List<Relocator>,
  private val stats: ShadowStats,
) : Remapper() {

  private val classPattern: Pattern = Pattern.compile("(\\[*)?L(.+)")

  public fun hasRelocators(): Boolean = relocators.isNotEmpty()

  override fun mapValue(value: Any): Any {
    return if (value is String) {
      mapName(value, true)
    } else {
      super.mapValue(value)
    }
  }

  override fun map(name: String): String {
    return mapName(name, false)
  }

  public fun mapPath(path: String): String {
    return map(path.substring(0, path.indexOf('.')))
  }

  public fun mapPath(path: ShadowCopyAction.RelativeArchivePath): String {
    return mapPath(path.pathString)
  }

  private fun mapName(name: String, relocateClass: Boolean): String {
    var newName = name
    var mappedValue = name

    var prefix = ""
    var suffix = ""

    val matcher = classPattern.matcher(newName)
    if (matcher.matches()) {
      prefix = matcher.group(1) + "L"
      suffix = ""
      newName = matcher.group(2)
    }

    for (relocator in relocators) {
      if (relocator.canRelocateClass(newName) && relocateClass) {
        val classContext = RelocateClassContext.builder().className(newName).stats(stats).build()
        mappedValue = prefix + relocator.relocateClass(classContext) + suffix
        break
      } else if (relocator.canRelocatePath(newName)) {
        val pathContext = RelocatePathContext.builder().path(newName).stats(stats).build()
        mappedValue = prefix + relocator.relocatePath(pathContext) + suffix
        break
      }
    }

    return mappedValue
  }
}
