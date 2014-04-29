package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.tasks.bundling.Jar

class ShadowCopy extends Jar {

    List<Transformer> transformers = []

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry)
        return new ShadowCopyAction(getArchivePath(), getCompressor(), documentationRegistry, transformers)
    }

    ShadowCopy transformer(Class<? extends Transformer> clazz, Closure c) {
        Transformer transformer = clazz.newInstance()
        if (c) {
            c.delegate = transformer
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c(transformer)
        }
        transformers << transformer
        return this
    }
}
