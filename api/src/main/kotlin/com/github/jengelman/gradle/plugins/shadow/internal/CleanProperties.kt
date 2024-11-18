package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.BufferedWriter
import java.io.IOException
import java.io.Writer
import java.util.Date
import java.util.Properties

internal class CleanProperties : Properties() {
    @Throws(IOException::class)
    override fun store(writer: Writer, comments: String) {
        super.store(StripCommentsWithTimestampBufferedWriter(writer), comments)
    }

    private class StripCommentsWithTimestampBufferedWriter(out: Writer) : BufferedWriter(out) {
        private val lengthOfExpectedTimestamp = ("#" + Date().toString()).length

        @Throws(IOException::class)
        override fun write(str: String) {
            if (str.couldBeCommentWithTimestamp) return
            super.write(str)
        }

        private val String?.couldBeCommentWithTimestamp: Boolean get() {
            return this != null && startsWith("#") && length == lengthOfExpectedTimestamp
        }
    }
}
