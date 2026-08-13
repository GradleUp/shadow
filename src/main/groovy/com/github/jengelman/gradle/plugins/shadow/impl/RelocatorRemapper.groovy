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
            String name = (String) object
            String value = name

            Matcher m = classPattern.matcher(name)
            if (m.find()) {
                StringBuffer sb = new StringBuffer()
                do {
                    String prefix = (m.group(1) ?: "") + "L"
                    String className = m.group(2)
                    String relocated = className
                    for (Relocator r : relocators) {
                        if (r instanceof SimpleRelocator && ((SimpleRelocator) r).rawString) {
                            continue
                        }
                        if (r.canRelocateClass(className)) {
                            RelocateClassContext classContext = RelocateClassContext.builder().className(className).stats(stats).build()
                            relocated = r.relocateClass(classContext)
                            break
                        } else if (r.canRelocatePath(className)) {
                            RelocatePathContext pathContext = RelocatePathContext.builder().path(className).stats(stats).build()
                            relocated = r.relocatePath(pathContext)
                            break
                        }
                    }
                    m.appendReplacement(sb, Matcher.quoteReplacement(prefix + relocated + ";"))
                } while (m.find())
                m.appendTail(sb)
                value = sb.toString()
            }

            return value
        }

        return super.mapValue(object)
    }

    @Override
    String map(String name) {
        String value = name

        Matcher m = classPattern.matcher(name)
        if (m.find()) {
            StringBuffer sb = new StringBuffer()
            do {
                String prefix = (m.group(1) ?: "") + "L"
                String className = m.group(2)
                String relocated = className
                for (Relocator r : relocators) {
                    if (r.canRelocatePath(className)) {
                        RelocatePathContext pathContext = RelocatePathContext.builder().path(className).stats(stats).build()
                        relocated = r.relocatePath(pathContext)
                        break
                    }
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(prefix + relocated + ";"))
            } while (m.find())
            m.appendTail(sb)
            value = sb.toString()
        }

        for (Relocator r : relocators) {
            if (r.canRelocatePath(name)) {
                RelocatePathContext pathContext = RelocatePathContext.builder().path(name).stats(stats).build()
                value = r.relocatePath(pathContext)
                break
            }
        }

        return value
    }

    String mapPath(String path) {
        map(path.substring(0, path.indexOf('.')))
    }

    String mapPath(RelativeArchivePath path) {
        mapPath(path.pathString)
    }

}
