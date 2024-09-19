package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.jar.JarFile

internal object RelocationUtil {

  @JvmStatic
  fun configureRelocation(target: ShadowJar, prefix: String) {
    target.configurations
      .asSequence()
      .flatMap { it.files }
      .flatMap { JarFile(it).entries().asSequence() }
      .filter { it.name.endsWith(".class") && it.name != "module-info.class" }
      .forEach {
        val pkg = it.name.substring(0, it.name.lastIndexOf('/')).replace('/', '.')
        target.relocate(pkg, "$prefix.$pkg")
      }
  }
}
