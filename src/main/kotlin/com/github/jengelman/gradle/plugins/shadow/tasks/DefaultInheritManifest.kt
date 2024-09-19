package com.github.jengelman.gradle.plugins.shadow.tasks

import groovy.lang.Closure
import java.io.Writer
import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.java.archives.Attributes
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestException
import org.gradle.api.java.archives.ManifestMergeSpec
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.java.archives.internal.DefaultManifestMergeSpec

class DefaultInheritManifest(
  private val fileResolver: FileResolver,
) : InheritManifest {
  private val inheritMergeSpecs: MutableList<DefaultManifestMergeSpec> = ArrayList()
  private val internalManifest: Manifest = DefaultManifest(fileResolver)

  override fun inheritFrom(vararg inheritPaths: Any): InheritManifest {
    inheritFrom(inheritPaths, action = null)
    return this
  }

  override fun inheritFrom(vararg inheritPaths: Any, action: Action<InheritManifest>?): InheritManifest {
    val mergeSpec = DefaultManifestMergeSpec()
    mergeSpec.from(inheritPaths)
    inheritMergeSpecs.add(mergeSpec)
    action?.execute(this)
    return this
  }

  override fun getAttributes(): Attributes {
    return internalManifest.attributes
  }

  override fun getSections(): Map<String, Attributes> {
    return internalManifest.sections
  }

  @Throws(ManifestException::class)
  override fun attributes(attributes: Map<String, *>): Manifest {
    internalManifest.attributes(attributes)
    return this
  }

  @Throws(ManifestException::class)
  override fun attributes(attributes: Map<String, *>, sectionName: String): Manifest {
    internalManifest.attributes(attributes, sectionName)
    return this
  }

  override fun getEffectiveManifest(): DefaultManifest {
    var base = DefaultManifest(fileResolver)
    inheritMergeSpecs.forEach {
      base = it.merge(base, fileResolver)
    }
    base.from(internalManifest)
    return base.effectiveManifest
  }

  fun writeTo(writer: Writer): Manifest = apply {
    this.effectiveManifest.writeTo(writer)
  }

  override fun writeTo(path: Any): Manifest = apply {
    this.effectiveManifest.writeTo(path)
  }

  override fun from(vararg mergePath: Any): Manifest = apply {
    internalManifest.from(*mergePath)
  }

  override fun from(mergePath: Any, closure: Closure<*>?): Manifest = apply {
    internalManifest.from(mergePath, closure)
  }

  override fun from(mergePath: Any, action: Action<ManifestMergeSpec>): Manifest = apply {
    internalManifest.from(mergePath, action)
  }
}
