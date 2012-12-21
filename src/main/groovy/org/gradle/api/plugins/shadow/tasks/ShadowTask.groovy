package org.gradle.api.plugins.shadow.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.plugins.shadow.ShadowStats
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

    boolean statsEnabled

    List<RelativePath> existingPaths = []

    private List<File> jarCache

    ShadowStats stats

    @TaskAction
    void shadow() {
        logger.info "${NAME.capitalize()} - start"
        initStats()

        JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar))

        jars.each { File jar ->
            processJar(jar, jos)
        }
        IOUtil.close(jos)
        logger.info "${NAME.capitalize()} - finish"
        printStats()
    }

    private void initStats() {
        statsEnabled = project.shadow.stats
        if (statsEnabled) {
            stats = new ShadowStats()
            stats.jarCount = jars.size()
            logger.info "${NAME.capitalize()} - total jars [${stats.jarCount}]"
        }
    }

    private void printStats() {
        if (statsEnabled) {
            stats.printStats()
        }
    }

    void processJar(File file, JarOutputStream jos) {
        logger.debug "${NAME.capitalize()} - shadowing [${file.name}]"
        withStats {
            def filteredTree = project.zipTree(file).matching(filter)
            def jarFile = new JarFile(file)
            filteredTree.visit { FileTreeElement jarEntry ->
                processJarEntry(jarEntry, jarFile, jos)
            }
        }

    }

    void withStats(Closure c) {
        if (statsEnabled) {
            stats.startJar()
        }
        c()
        if (statsEnabled) {
            stats.finishJar()
            logger.trace "${NAME.capitalize()} - shadowed in ${stats.jarTiming} ms"
        }
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
        if (!jarCache) {
            jarCache = artifacts + dependencies
        }
        jarCache
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
