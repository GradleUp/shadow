package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.jar.JarFile

internal object RelocationUtil {

  @JvmStatic
  fun configureRelocation(target: ShadowJar, prefix: String) {
    val packages = mutableSetOf<String>()
    target.configurations.forEach { configuration ->
      configuration.files.forEach { jar ->
        JarFile(jar).use { jf ->
          for (entry in jf.entries()) {
            if (entry.name.endsWith(".class") && entry.name != "module-info.class") {
              packages += entry.name.substring(0, entry.name.lastIndexOf('/')).replace('/', '.')
            }
          }
        }
      }
    }
    packages.forEach {
      target.relocate(it, "$prefix.$it")
    }
  }
}
