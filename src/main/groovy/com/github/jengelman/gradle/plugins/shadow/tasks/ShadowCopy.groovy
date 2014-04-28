package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.tasks.bundling.Jar

class ShadowCopy extends Jar {

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry)
        return new ShadowCopyAction(getArchivePath(), getCompressor(), documentationRegistry)
    }
}
