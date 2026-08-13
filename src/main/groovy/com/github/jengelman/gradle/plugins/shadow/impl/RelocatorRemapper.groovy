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
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction.RelativeArchivePath
import groovy.transform.CompileStatic
import org.vafer.jdeb.shaded.objectweb.asm.commons.Remapper

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Modified from org.apache.maven.plugins.shade.DefaultShader.java#RelocatorRemapper
 *
 * @author John Engelman
 */
@CompileStatic
class RelocatorRemapper extends Remapper {

    private final Pattern classPattern = Pattern.compile("([\\[()BCDFIJSZ]*)?L([^;]+)(;?)")

    List<Relocator> relocators
    ShadowStats stats

    RelocatorRemapper(List<Relocator> relocators, ShadowStats stats) {
        this.relocators = relocators
        this.stats = stats
    }

    boolean hasRelocators() {
        return !relocators.empty
    }

    @Override
    Object mapValue(Object object) {
        if (object instanceof String) {
            return mapName((String) object, true)
        }
        return super.mapValue(object)
    }

    @Override
    String map(String name) {
        return mapName(name, false)
    }

    String mapName(String name, boolean mapLiterals) {
        String[] parts = name.split(";", -1)
        List<String> mapped = new ArrayList<>(parts.length)
        for (String part : parts) {
            mapped.add(realMap(part, mapLiterals))
        }
        return mapped.join(";")
    }

    private String realMap(String name, boolean mapLiterals) {
        String newName = name
        String prefix = ""
        String suffix = ""

        Matcher m = classPattern.matcher(newName)
        if (m.matches()) {
            prefix = (m.group(1) ?: "") + "L"
            suffix = m.group(3) ?: ""
            newName = m.group(2)
        }

        for (Relocator r : relocators) {
            if (mapLiterals && r instanceof SimpleRelocator && ((SimpleRelocator) r).rawString) {
                continue
            }
            if (r.canRelocateClass(newName)) {
                RelocateClassContext classContext = RelocateClassContext.builder().className(newName).stats(stats).build()
                return prefix + r.relocateClass(classContext) + suffix
            } else if (r.canRelocatePath(newName)) {
                RelocatePathContext pathContext = RelocatePathContext.builder().path(newName).stats(stats).build()
                return prefix + r.relocatePath(pathContext) + suffix
            }
        }

        return name
    }

    String mapPath(String path) {
        map(path.substring(0, path.indexOf('.')))
    }

    String mapPath(RelativeArchivePath path) {
        mapPath(path.pathString)
    }

}
