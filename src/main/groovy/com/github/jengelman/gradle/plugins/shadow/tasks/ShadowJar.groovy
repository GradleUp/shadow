package com.github.jengelman.gradle.plugins.shadow.tasks

import static org.gradle.api.tasks.bundling.ZipEntryCompression.*

import com.github.jengelman.gradle.plugins.shadow.internal.DefaultZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.util.ConfigureUtil

class ShadowJar extends Jar {

    List<Transformer> transformers = []
    List<Relocator> relocators = []

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry)
        return new ShadowCopyAction(getArchivePath(), getCustomCompressor(), documentationRegistry, transformers, relocators)
    }

    protected ZipCompressor getCustomCompressor() {
        switch (entryCompression) {
            case DEFLATED:
                return new DefaultZipCompressor(zip64, ZipOutputStream.DEFLATED)
            case STORED:
                return new DefaultZipCompressor(zip64, ZipOutputStream.STORED)
            default:
                throw new IllegalArgumentException(String.format('Unknown Compression type %s', entryCompression))
        }
    }

    ShadowJar transformer(Class<? extends Transformer> clazz, Closure c = null) {
        Transformer transformer = clazz.newInstance()
        if (c) {
            c.delegate = transformer
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c(transformer)
        }
        transformers << transformer
        return this
    }

    public ShadowJar appendManifest(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getManifest())
        return this
    }

    public ShadowJar exclude(Dependency dependency) {
        project.configurations.runtime.files(dependency).each {
            this.exclude(it.path.substring(it.path.lastIndexOf('/')+1))
        }
        return this
    }

    public Dependency dependency(Object notation, Closure configuration = null) {
        project.dependencies.create(notation, configuration)
    }

}
