package com.github.jengelman.gradle.plugins.shadow.util

import org.codehaus.plexus.util.IOUtil

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class JarBuilder {

    List<String> entries = []
    JarOutputStream jos

    JarBuilder(OutputStream os) {
        jos = new JarOutputStream(os)
    }

    private void addDirectory(String name) {
        if (!entries.contains(name)) {
            if (name.lastIndexOf('/') > 0) {
                String parent = name.substring(0, name.lastIndexOf('/'))
                if (!entries.contains(parent)) {
                    addDirectory(parent)
                }
            }

            // directory entries must end in "/"
            JarEntry entry = new JarEntry(name + "/")
            jos.putNextEntry(entry)

            entries.add(name)
        }
    }

    JarBuilder withFile(String path, String data) {
        def idx = path.lastIndexOf('/')
        if (idx != -1) {
            addDirectory(path.substring(0, idx))
        }
        if (!entries.contains(path)) {
            JarEntry entry = new JarEntry(path)
            jos.putNextEntry(entry)
            entries << path
            IOUtil.copy(data.bytes, jos)
        }
        return this
    }

    void build() {
        jos.close()
    }
}
