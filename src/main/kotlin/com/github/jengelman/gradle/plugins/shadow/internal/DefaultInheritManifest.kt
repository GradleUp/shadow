package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.InheritManifest
import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.java.archives.internal.DefaultManifestMergeSpec

internal class DefaultInheritManifest(
    private val fileResolver: FileResolver,
    private val internalManifest: Manifest = DefaultManifest(fileResolver),
) : InheritManifest,
    Manifest by internalManifest {
    private val inheritMergeSpecs = mutableListOf<DefaultManifestMergeSpec>()

    override fun inheritFrom(vararg inheritPaths: Any): InheritManifest = apply {
        inheritFrom(inheritPaths, action = null)
    }

    override fun inheritFrom(vararg inheritPaths: Any, action: Action<InheritManifest>?): InheritManifest = apply {
        val mergeSpec = DefaultManifestMergeSpec()
        mergeSpec.from(inheritPaths)
        inheritMergeSpecs.add(mergeSpec)
        action?.execute(this)
    }

    override fun getEffectiveManifest(): Manifest {
        var base = DefaultManifest(fileResolver)
        inheritMergeSpecs.forEach {
            base = it.merge(base, fileResolver)
        }
        base.from(internalManifest)
        return base.effectiveManifest
    }
}
