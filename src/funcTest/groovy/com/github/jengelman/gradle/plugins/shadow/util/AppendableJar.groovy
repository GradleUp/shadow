package com.github.jengelman.gradle.plugins.shadow.util

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

    File write() {
        JarBuilder builder = new JarBuilder(file.newOutputStream())
        contents.each { path, contents ->
            builder.withFile(path, contents)
        }
        builder.build()
        return file
    }
}
