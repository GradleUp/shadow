package org.gradle.api.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement

import java.util.jar.JarFile
import java.util.jar.JarOutputStream

public interface Transformer {
    boolean canTransformResource(FileTreeElement entry)
    void transform(FileTreeElement entry, JarFile jar, JarOutputStream jos)
    void modifyOutputStream(JarOutputStream jos)
}
