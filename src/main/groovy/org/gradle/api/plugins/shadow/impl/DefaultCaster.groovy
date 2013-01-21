package org.gradle.api.plugins.shadow.impl

import groovy.util.logging.Slf4j

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.gradle.api.plugins.shadow.ShadowStats
import org.gradle.api.plugins.shadow.filter.Filter
import org.gradle.api.plugins.shadow.relocation.Relocator
import org.gradle.api.plugins.shadow.tasks.CastTask
import org.gradle.api.plugins.shadow.transformers.ManifestResourceTransformer
import org.gradle.api.plugins.shadow.transformers.Transformer
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.mvn3.org.codehaus.plexus.util.IOUtil
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.RemappingClassAdapter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipException

/**
 * @author Jason van Zyl
 *
 * Modified from org.apache.maven.plugins.shade.DefaultShader.java
 */
@Slf4j
public class DefaultCaster implements Caster {

    public void cast(ShadowRequest shadowRequest) {
        Set<String> resources = new HashSet<String>()

        Transformer manifestTransformer = null
        List<Transformer> transformers = new ArrayList<Transformer>(shadowRequest.getResourceTransformers())
        for (Iterator<Transformer> it = transformers.iterator(); it.hasNext();) {
            Transformer transformer = it.next()
            if (transformer instanceof ManifestResourceTransformer) {
                manifestTransformer = transformer
                it.remove()
            }
        }

        RelocatorRemapper remapper = new RelocatorRemapper(shadowRequest.getRelocators())

        JarOutputStream jos = new JarOutputStream(new FileOutputStream(shadowRequest.getUberJar()))

        if (manifestTransformer != null) {
            for (File jar : shadowRequest.getJars()) {
                JarFile jarFile = newJarFile(jar)
                for (Enumeration<JarEntry> en = jarFile.entries(); en.hasMoreElements();) {
                    JarEntry entry = en.nextElement()
                    String resource = entry.getName()
                    if (manifestTransformer.canTransformResource(resource)) {
                        resources.add(resource)
                        manifestTransformer.transform(resource, jarFile.getInputStream(entry), shadowRequest.getRelocators())
                        break;
                    }
                }
            }
            if (manifestTransformer.hasTransformedResource()) {
                manifestTransformer.modifyOutputStream(jos)
            }
        }

        for (File jar : shadowRequest.getJars()) {
            withStats(shadowRequest.stats) {

                log.debug("Processing JAR " + jar)

                List<Filter> jarFilters = getFilters(jar, shadowRequest.getFilters())
                JarFile jarFile = newJarFile(jar)

                for (Enumeration<JarEntry> j = jarFile.entries(); j.hasMoreElements();) {
                    JarEntry entry = j.nextElement()
                    String name = entry.getName()

                    if ("META-INF/INDEX.LIST".equals(name)) {
                        // we cannot allow the jar indexes to be copied over or the
                        // jar is useless. Ideally, we could create a new one
                        // later
                        continue;
                    }

                    if (!entry.isDirectory() && !isFiltered(jarFilters, name)) {
                        InputStream is = jarFile.getInputStream(entry)
                        String mappedName = remapper.map(name)
                        int idx = mappedName.lastIndexOf('/')
                        if (idx != -1) {
                            // make sure dirs are created
                            String dir = mappedName.substring(0, idx)
                            if (!resources.contains(dir)) {
                                addDirectory(resources, jos, dir)
                            }
                        }

                        if (name.endsWith(".class")) {
                            addRemappedClass(remapper, jos, jar, name, is)
                        } else if (shadowRequest.isShadeSourcesContent() && name.endsWith(".java")) {
                            // Avoid duplicates
                            if (resources.contains(mappedName)) {
                                continue;
                            }

                            addJavaSource(resources, jos, mappedName, is, shadowRequest.getRelocators())
                        } else {
                            if (!resourceTransformed(transformers, mappedName, is, shadowRequest.getRelocators())) {
                                // Avoid duplicates that aren't accounted for by the resource transformers
                                if (resources.contains(mappedName)) {
                                    continue;
                                }

                                addResource(resources, jos, mappedName, is)
                            }
                        }

                        IOUtil.close(is)
                    }
                }

                jarFile.close()
            }
        }

        for (Transformer transformer : transformers) {
            if (transformer.hasTransformedResource()) {
                transformer.modifyOutputStream(jos)
            }
        }

        IOUtil.close(jos)

        for (Filter filter : shadowRequest.getFilters()) {
            filter.finished()
        }
    }

    private JarFile newJarFile(File jar) {
        try {
            return new JarFile(jar)
        }
        catch (ZipException zex) {
            // JarFile is not very verbose and doesn't tell the user which file it was
            // so we will create a new Exception instead
            throw new ZipException("error in opening zip file " + jar)
        }
    }

    private List<Filter> getFilters(File jar, List<Filter> filters) {
        List<Filter> list = new ArrayList<Filter>()

        for (Filter filter : filters) {
            if (filter.canFilter(jar)) {
                list.add(filter)
            }

        }

        return list
    }

    private void addDirectory(Set<String> resources, JarOutputStream jos, String name) {
        if (name.lastIndexOf('/') > 0) {
            String parent = name.substring(0, name.lastIndexOf('/'))
            if (!resources.contains(parent)) {
                addDirectory(resources, jos, parent)
            }
        }

        // directory entries must end in "/"
        JarEntry entry = new JarEntry(name + "/")
        jos.putNextEntry(entry)

        resources.add(name)
    }

    private void addRemappedClass(RelocatorRemapper remapper, JarOutputStream jos, File jar, String name,
                                  InputStream is) {
        if (!remapper.hasRelocators()) {
            try {
                jos.putNextEntry(new JarEntry(name))
                IOUtil.copy(is, jos)
            }
            catch (ZipException e) {
                log.warn("We have a duplicate " + name + " in " + jar)
            }

            return
        }

        ClassReader cr = new ClassReader(is)

        // We don't pass the ClassReader here. This forces the ClassWriter to rebuild the constant pool.
        // Copying the original constant pool should be avoided because it would keep references
        // to the original class names. This is not a problem at runtime (because these entries in the
        // constant pool are never used), but confuses some tools such as Felix' maven-bundle-plugin
        // that use the constant pool to determine the dependencies of a class.
        ClassWriter cw = new ClassWriter(0)

        ClassVisitor cv = new RemappingClassAdapter(cw, remapper)

        try {
            cr.accept(cv, ClassReader.EXPAND_FRAMES)
        }
        catch (Throwable ise) {
            throw new TaskExecutionException("Error in ASM processing class " + name, ise)
        }

        byte[] renamedClass = cw.toByteArray()

        // Need to take the .class off for remapping evaluation
        String mappedName = remapper.map(name.substring(0, name.indexOf('.')))

        try {
            // Now we put it back on so the class file is written out with the right extension.
            jos.putNextEntry(new JarEntry(mappedName + ".class"))

            IOUtil.copy(renamedClass, jos)
        }
        catch (ZipException e) {
            log.warn("We have a duplicate " + mappedName + " in " + jar)
        }
    }

    private boolean isFiltered(List<Filter> filters, String name) {
        for (Filter filter : filters) {
            if (filter.isFiltered(name)) {
                return true
            }
        }

        return false
    }

    private boolean resourceTransformed(List<Transformer> resourceTransformers, String name, InputStream is,
                                        List<Relocator> relocators) {
        boolean resourceTransformed = false

        for (Transformer transformer : resourceTransformers) {
            if (transformer.canTransformResource(name)) {
                log.debug("Transforming " + name + " using " + transformer.getClass().getName())

                transformer.transform(name, is, relocators)

                resourceTransformed = true

                break
            }
        }
        return resourceTransformed
    }

    private void addJavaSource(Set<String> resources, JarOutputStream jos, String name, InputStream is,
                               List<Relocator> relocators)
    throws IOException {
        jos.putNextEntry(new JarEntry(name))

        String sourceContent = IOUtil.toString(new InputStreamReader(is, "UTF-8"))

        for (Relocator relocator : relocators) {
            sourceContent = relocator.applyToSourceContent(sourceContent)
        }

        OutputStreamWriter writer = new OutputStreamWriter(jos, "UTF-8")
        IOUtil.copy(sourceContent, writer)
        writer.flush()

        resources.add(name)
    }

    private void addResource(Set<String> resources, JarOutputStream jos, String name, InputStream is) {
        jos.putNextEntry(new JarEntry(name))

        IOUtil.copy(is, jos)

        resources.add(name)
    }

    void withStats(ShadowStats stats, Closure c) {
        if (stats) {
            stats.startJar()
        }
        c()
        if (stats) {
            stats.finishJar()
            log.trace "${CastTask.NAME.capitalize()} - shadowed in ${stats.jarTiming} ms"
        }
    }

}
