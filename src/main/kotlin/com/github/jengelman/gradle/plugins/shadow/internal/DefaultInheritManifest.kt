@file:Suppress("DEPRECATION", "InternalGradleApiUsage") // TODO: remove this in Shadow 10.

package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.InheritManifest
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestMergeSpec
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.java.archives.internal.DefaultManifestMergeSpec

internal class DefaultInheritManifest(
  project: Project,
  // `AbstractTask.getServices` is protected, we need to get it via `DefaultProject`.
  // https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/java/org/gradle/api/internal/AbstractTask.java#L194
  private val fileResolver: FileResolver = (project as DefaultProject).services.get(FileResolver::class.java),
  private val internalManifest: Manifest = DefaultManifest(fileResolver),
) : InheritManifest,
  Manifest by internalManifest {
  private val inheritMergeSpecs = mutableListOf<DefaultManifestMergeSpec>()

  override fun inheritFrom(
    vararg inheritPaths: Any,
    action: Action<ManifestMergeSpec>,
  ) {
    val mergeSpec = DefaultManifestMergeSpec()
    mergeSpec.from(*inheritPaths)
    inheritMergeSpecs.add(mergeSpec)
    action.execute(mergeSpec)
  }

  override fun getEffectiveManifest(): DefaultManifest {
    var base = DefaultManifest(fileResolver)
    inheritMergeSpecs.forEach {
      base = it.merge(base, fileResolver)
    }
    base.from(internalManifest)
    return base.effectiveManifest
  }

  override fun writeTo(path: Any): Manifest = apply {
    effectiveManifest.writeTo(path)
  }
}
