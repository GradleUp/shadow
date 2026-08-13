package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import groovy.transform.Canonical

@Canonical
class TransformerContext {

    String path
    InputStream is
    List<Relocator> relocators
    ShadowStats stats

    /**
     * @deprecated Use TransformerContext constructor instead.
     */
    @Deprecated
    static class Builder {
        private String path
        private InputStream is
        private List<Relocator> relocators
        private ShadowStats stats

        Builder path(String path) { this.path = path; return this }
        Builder is(InputStream is) { this.is = is; return this }
        Builder relocators(List<Relocator> relocators) { this.relocators = relocators; return this }
        Builder stats(ShadowStats stats) { this.stats = stats; return this }

        TransformerContext build() {
            return new TransformerContext(path, is, relocators, stats)
        }
    }

    /**
     * @deprecated Use TransformerContext constructor instead.
     */
    @Deprecated
    static Builder builder() {
        return new Builder()
    }

    static long getEntryTimestamp(boolean preserveFileTimestamps, long entryTime) {
        preserveFileTimestamps ? entryTime : ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    }
}
