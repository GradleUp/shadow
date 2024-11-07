package com.github.jengelman.gradle.plugins.shadow.tasks

import groovy.lang.Closure
import java.io.Writer
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.java.archives.Attributes
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestException
import org.gradle.api.java.archives.ManifestMergeSpec
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.java.archives.internal.DefaultManifestMergeSpec

public class DefaultInheritManifest(
  @Transient private val project: Project,
  private val fileResolver: FileResolver,
) : InheritManifest {
  private val internalManifest = DefaultManifest(fileResolver)
  private val inheritMergeSpecs = mutableListOf<DefaultManifestMergeSpec>()

  override fun inheritFrom(vararg inheritPaths: Any): InheritManifest {
    return inheritFrom(inheritPaths, closure = null)
  }

  override fun inheritFrom(vararg inheritPaths: Any, closure: Closure<*>?): InheritManifest = apply {
    val mergeSpec = DefaultManifestMergeSpec()
    mergeSpec.from(*inheritPaths)
    inheritMergeSpecs.add(mergeSpec)
    if (closure != null) {
      project.configure(mergeSpec, closure)
    }
  }

  override fun getAttributes(): Attributes = internalManifest.attributes

  override fun getSections(): MutableMap<String, Attributes> = internalManifest.sections

  @Throws(ManifestException::class)
  override fun attributes(map: Map<String, *>): Manifest = apply {
    internalManifest.attributes(map)
  }

  @Throws(ManifestException::class)
  override fun attributes(map: Map<String, *>, s: String): Manifest  = apply {
    internalManifest.attributes(map, s)
  }

  override fun getEffectiveManifest(): DefaultManifest {
    var base = DefaultManifest(fileResolver)
    inheritMergeSpecs.forEach {
      base = it.merge(base, fileResolver)
    }
    base.from(internalManifest)
    return base.effectiveManifest
  }

  public fun writeTo(writer: Writer): Manifest = apply {
    effectiveManifest.writeTo(writer)
  }

  override fun writeTo(o: Any): Manifest = apply {
    effectiveManifest.writeTo(o)
  }

  override fun from(vararg objects: Any): Manifest = apply {
    internalManifest.from(*objects)
  }

  override fun from(o: Any, closure: Closure<*>?): Manifest = apply {
    internalManifest.from(o, closure)
  }

  override fun from(o: Any, action: Action<ManifestMergeSpec>): Manifest = apply {
    internalManifest.from(o, action)
  }
}
