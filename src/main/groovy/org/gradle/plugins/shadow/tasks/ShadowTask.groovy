package org.gradle.plugins.shadow.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class ShadowTask extends DefaultTask {

    static final String NAME = "shadow"
    static final String DESC = "Combines all classpath resources into a single jar"
    static final String GROUP = "Shadow"

    @OutputFile
    File outputJar = project.shadow.shadowJar

    @Input
    List<String> includes = project.shadow.includes

    @Input
    List<String> excludes = project.shadow.excludes

    @InputFiles
    List<File> artifacts = project.configurations.getByName("runtime").allArtifacts.files as List

    @TaskAction
    void shadow() {
        logger.info "${NAME.capitalize()} - start"
        logger.info "${NAME.capitalize()} - total jars [${jars.size()}]"
        def startTime = System.currentTimeMillis()

        JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar))

        List<RelativePath> existingPaths = []
        jars.each { File jar ->
            logger.debug "${NAME.capitalize()} - shadowing [${jar.name}]"
            def fileTime = System.currentTimeMillis()
            project.zipTree(jar).matching(filter).visit { FileTreeElement jarEntry ->
                if (!jarEntry.isDirectory() && !existingPaths.contains(jarEntry.relativePath)) {
                    existingPaths << jarEntry.relativePath
                    writeJarEntry jos, jarEntry
                }
            }
            logger.trace "${NAME.capitalize()} - shadowed in ${System.currentTimeMillis() - fileTime} ms"
        }
        jos.close()
        logger.info "${NAME.capitalize()} - finish [${(System.currentTimeMillis() - startTime)/1000} s]"
    }

    PatternFilterable getFilter() {
        PatternFilterable filter = new PatternSet()
        filter.includes = includes
        filter.excludes = excludes
        filter
    }

    List<File> getJars() {
        artifacts + dependencies
    }

    List<File> getDependencies() {
        project.configurations.getByName("runtime").resolve() as List
    }

    static void writeJarEntry(JarOutputStream jos, String path, byte[] bytes) {
        jos.putNextEntry(new JarEntry(path))
        jos.write(bytes)
        jos.closeEntry()
    }

    static void writeJarEntry(JarOutputStream jos, RelativePath rPath, byte[] bytes) {
        writeJarEntry(jos, rPath.toString(), bytes)
    }

    static void writeJarEntry(JarOutputStream jos, FileTreeElement entry) {
        writeJarEntry jos, entry.relativePath, entry.file.bytes
    }
}
