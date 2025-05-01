package com.github.jengelman.gradle.plugins.shadow.internal

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

/**
 * Modified from [org.apache.maven.plugins.shade.DefaultShader.ShadeClassRemapper.java](https://github.com/apache/maven-shade-plugin/blob/5115e41e66ac19f10b661e63f8de76ad3e5905d2/src/main/java/org/apache/maven/plugins/shade/DefaultShader.java#L808-L844).
 */
internal class ShadowClassRemapper(
  classVisitor: ClassVisitor,
  private val packageMapper: PackageMapper,
  private val pkg: String,
  remapper: Remapper = object : Remapper() {
    override fun mapValue(value: Any): Any {
      return if (value is String) {
        packageMapper.map(value, mapPaths = true, mapPackages = true)
      } else {
        super.mapValue(value)
      }
    }

    override fun map(internalName: String): String {
      return packageMapper.map(internalName, mapPaths = true, mapPackages = false)
    }
  },
) : ClassRemapper(classVisitor, remapper),
  PackageMapper {
  private var remapped = false

  override fun visitSource(source: String?, debug: String?) {
    if (source == null) return super.visitSource(null, debug)

    val fqSource: String = pkg + source
    val mappedSource = map(fqSource, mapPaths = true, mapPackages = false)
    val filename = mappedSource.substring(mappedSource.lastIndexOf('/') + 1)
    super.visitSource(filename, debug)
  }

  override fun map(entityName: String, mapPaths: Boolean, mapPackages: Boolean): String {
    val mapped = packageMapper.map(entityName, mapPaths, mapPackages)
    if (!remapped) {
      remapped = mapped != entityName
    }
    return mapped
  }
}
