package com.github.jengelman.gradle.plugins.shadow.transformers

import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Named
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Internal

/**
 * Modified from `org.apache.maven.plugins.shade.resource.ResourceTransformer.java`
 *
 * @author Jason van Zyl
 * @author Charlie Knudsen
 * @author John Engelman
 */
interface Transformer : Named {
    fun canTransformResource(element: FileTreeElement): Boolean

    fun transform(context: TransformerContext)

    fun hasTransformedResource(): Boolean

    fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean)

    @Internal
    override fun getName(): String = this::class.java.simpleName
}

object NoOpTransformer : Transformer {
    override fun canTransformResource(element: FileTreeElement): Boolean = false
    override fun transform(context: TransformerContext): Unit = Unit
    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean): Unit = Unit
    override fun hasTransformedResource(): Boolean = false
}
