package com.github.jengelman.gradle.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFileTreeElement
import org.gradle.testfixtures.ProjectBuilder

abstract class TransformerTestSupport<T extends Transformer> {
    protected static T transformer
    protected static final def objectFactory = ProjectBuilder.builder().build().objects

    protected static FileTreeElement getFileElement(String path) {
        return new DefaultFileTreeElement(null, RelativePath.parse(true, path), null, null)
    }
}
