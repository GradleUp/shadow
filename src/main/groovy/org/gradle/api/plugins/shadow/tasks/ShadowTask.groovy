package org.gradle.api.plugins.shadow.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.mvn3.org.codehaus.plexus.util.IOUtil

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class ShadowTask extends DefaultTask {

    static final String NAME = "shadow"
    static final String DESC = "Combines all classpath resources into a single jar"

    @OutputFile
    File outputJar = project.shadow.shadowJar

    @Input
    List<String> includes = project.shadow.includes

    @Input
    List<String> excludes = project.shadow.excludes

    @InputFiles
    List<File> artifacts = project.configurations.getByName("runtime").allArtifacts.files as List

    List<RelativePath> existingPaths = []

    @TaskAction
    void shadow() {
        logger.info "${NAME.capitalize()} - start"
        logger.info "${NAME.capitalize()} - total jars [${jars.size()}]"
        def startTime = System.currentTimeMillis()

        JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar))

        jars.each { File jar ->
            processJar(jar, jos)
        }
        IOUtil.close(jos)
        logger.info "${NAME.capitalize()} - finish [${(System.currentTimeMillis() - startTime)/1000} s]"
    }

    void processJar(File file, JarOutputStream jos) {
        logger.debug "${NAME.capitalize()} - shadowing [${file.name}]"
        def fileTime = System.currentTimeMillis()
        def filteredTree = project.zipTree(file).matching(filter)
        def jarFile = new JarFile(file)
        filteredTree.visit { FileTreeElement jarEntry ->
            processJarEntry(jarEntry, jarFile, jos)
        }
        logger.trace "${NAME.capitalize()} - shadowed in ${System.currentTimeMillis() - fileTime} ms"
    }

    void processJarEntry(FileTreeElement entry, JarFile jar, JarOutputStream jos) {
        if (!entry.isDirectory() && !existingPaths.contains(entry.relativePath)) {
            existingPaths << entry.relativePath
            writeJarEntry jos, entry, jar
        }
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

    static void writeJarEntry(JarOutputStream jos, FileTreeElement entry, JarFile jar) {
        JarEntry jarEntry = jar.getJarEntry(entry.relativePath.toString())
        writeJarEntry jos, entry.relativePath, jarEntry, jar
    }

    static void writeJarEntry(JarOutputStream jos, RelativePath path, JarEntry entry, JarFile jar) {
        def is = jar.getInputStream(entry)
        jos.putNextEntry(new JarEntry(path.toString()))
        IOUtil.copy(is, jos)
        jos.closeEntry()
        IOUtil.close(is)
    }
}
