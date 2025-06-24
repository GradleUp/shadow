package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.jar.JarFile

class RelocationUtil {

    static void configureRelocation(ShadowJar target, String prefix) {
        def packages = [] as Set<String>
        target.configurations.each { configuration ->
            configuration.files.each { jar ->
                JarFile jf = new JarFile(jar)
                jf.entries().each { entry ->
                    if (entry.name.endsWith(".class") && entry.name != "module-info.class") {
                        def i = entry.name.lastIndexOf('/')
                        if (i != -1) {
                            packages << entry.name.take(i).replaceAll('/', '.')
                        }
                    }
                }
                jf.close()
            }
        }
        packages.each {
            target.relocate(it, "${prefix}.${it}")
        }
    }
}
