package com.github.jengelman.gradle.plugins.shadow.impl

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction.RelativeArchivePath
import org.objectweb.asm.commons.Remapper

import java.util.regex.Pattern

/**
 * Modified from org.apache.maven.plugins.shade.DefaultShader.java#RelocatorRemapper
 *
 * @author John Engelman
 */
class RelocatorRemapper extends Remapper {

    private final Pattern classPattern = Pattern.compile("(\\[*)?L(.+)")

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
            return map((String) object)
        }
        return super.mapValue(object)
    }

    @Override
    String map(String name) {
        String prefix = ""
        String suffix = ""

        def matcher = classPattern.matcher(name)
        if (matcher.matches()) {
            prefix = matcher.group(1) + "L"
            name = matcher.group(2)
        }

        for (Relocator relocator : relocators) {
            if (relocator.canRelocateClass(name)) {
                def context = RelocateClassContext.builder().className(name).stats(stats).build()
                return prefix + relocator.relocateClass(context) + suffix
            } else if (relocator.canRelocatePath(name)) {
                def context = RelocatePathContext.builder().path(name).stats(stats).build()
                return prefix + relocator.relocatePath(context) + suffix
            }
        }
        return name
    }

    String mapPath(String path) {
        return map(path.substring(0, path.indexOf('.')))
    }

    String mapPath(RelativeArchivePath path) {
        return mapPath(path.pathString)
    }
}
