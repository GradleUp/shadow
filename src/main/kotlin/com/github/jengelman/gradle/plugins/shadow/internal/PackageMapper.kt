package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import java.util.regex.Pattern

/**
 * Modified from [org.apache.maven.plugins.shade.DefaultShader.PackageMapper.java](https://github.com/apache/maven-shade-plugin/blob/5115e41e66ac19f10b661e63f8de76ad3e5905d2/src/main/java/org/apache/maven/plugins/shade/DefaultShader.java#L725-L735).
 */
internal interface PackageMapper {

  /**
   * Map an entity name according to the mapping rules known to this package mapper
   *
   * @param entityName entity name to be mapped
   * @param mapPaths map "slashy" names like paths or internal Java class names, e.g. `com/acme/Foo`?
   * @param mapPackages  map "dotty" names like qualified Java class or package names, e.g. `com.acme.Foo`?
   * @return mapped entity name, e.g. `org/apache/acme/Foo` or `org.apache.acme.Foo`
   */
  fun map(entityName: String, mapPaths: Boolean, mapPackages: Boolean): String
}

/**
 * Modified from [org.apache.maven.plugins.shade.DefaultShader.DefaultPackageMapper.java](https://github.com/apache/maven-shade-plugin/blob/5115e41e66ac19f10b661e63f8de76ad3e5905d2/src/main/java/org/apache/maven/plugins/shade/DefaultShader.java#L740-L774).
 */
internal class DefaultPackageMapper(
  private val relocators: Set<Relocator>,
) : PackageMapper {

  override fun map(entityName: String, mapPaths: Boolean, mapPackages: Boolean): String {
    var value = entityName
    var prefix = ""
    var suffix = ""

    val matcher = classPattern.matcher(value)
    if (matcher.matches()) {
      prefix = matcher.group(1) + "L"
      suffix = ";"
      value = matcher.group(2)
    }

    for (relocator in relocators) {
      if (mapPackages && relocator.canRelocateClass(value)) {
        return prefix + relocator.relocateClass(value) + suffix
      } else if (mapPaths && relocator.canRelocatePath(value)) {
        return prefix + relocator.relocatePath(value) + suffix
      }
    }

    return value
  }
}

private val classPattern: Pattern = Pattern.compile("(\\[*)?L(.+);")
