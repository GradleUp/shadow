package com.github.jengelman.gradle.plugins.shadow.transformers

import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

public object NoOpTransformer : Transformer {
    public override fun canTransformResource(element: FileTreeElement): Boolean = false
    public override fun transform(context: TransformerContext): Unit = Unit
    public override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean): Unit = Unit
    public override fun hasTransformedResource(): Boolean = false
}
