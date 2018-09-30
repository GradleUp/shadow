package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import groovy.transform.Canonical
import groovy.transform.builder.Builder


@Canonical
@Builder
class TransformerContext {

    String path
    InputStream is
    List<Relocator> relocators
    ShadowStats stats

    static long getEntryTimestamp(boolean preserveFileTimestamps, long entryTime) {
        preserveFileTimestamps ? entryTime : ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    }
}
