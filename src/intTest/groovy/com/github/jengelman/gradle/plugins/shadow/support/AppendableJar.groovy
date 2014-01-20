package com.github.jengelman.gradle.plugins.shadow.support

class AppendableJar {

    Map<String, String> contents = [:]
    File file

    AppendableJar(File file) {
        this.file = file
    }

    AppendableJar insertFile(String path, String content) {
        contents[path] = content
        return this
    }

    void write() {
        JarBuilder builder = new JarBuilder(file.newObjectOutputStream())
        contents.each { path, contents ->
            builder.withFile(path, contents)
        }
        builder.build()
    }
}
