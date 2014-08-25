package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.java.archives.internal.DefaultManifestMergeSpec
import org.gradle.util.ConfigureUtil

class DefaultInheritManifest extends DefaultManifest implements InheritManifest {

    private List<DefaultManifestMergeSpec> inheritMergeSpecs = []

    private final FileResolver fileResolver

    DefaultInheritManifest(FileResolver fileResolver) {
        super(fileResolver)
        this.fileResolver = fileResolver
    }

    public InheritManifest inheritFrom(Object... inheritPaths) {
        inheritFrom(inheritPaths, null)
        return this
    }

    public InheritManifest inheritFrom(Object inheritPaths, Closure closure) {
        DefaultManifestMergeSpec mergeSpec = new DefaultManifestMergeSpec()
        mergeSpec.from(inheritPaths)
        inheritMergeSpecs.add(mergeSpec)
        ConfigureUtil.configure(closure, mergeSpec)
        return this
    }

    @Override
    public DefaultManifest getEffectiveManifest() {
        DefaultManifest base = new DefaultManifest(fileResolver)
        inheritMergeSpecs.each {
            base = it.merge(base, fileResolver)
        }
        base.from this.asDefaultManifest()
        return base.getEffectiveManifest()
    }

    private DefaultManifest asDefaultManifest() {
        DefaultManifest newManifest = new DefaultManifest(fileResolver)
        newManifest.attributes this.attributes
        this.sections.each { section, attrs ->
            newManifest.attributes attrs, section
        }
        newManifest.mergeSpecs.addAll(this.mergeSpecs)
        return newManifest
    }
}
