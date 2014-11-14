package com.github.jengelman.gradle.plugins.shadow.internal.gradle111

import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.UncheckedIOException;

class Gradle111DefaultZipCompressor implements ZipCompressor {

    public static final ZipCompressor INSTANCE = new Gradle111DefaultZipCompressor()

    public Gradle111DefaultZipCompressor() {
    }

    public int getCompressedMethod() {
        return ZipOutputStream.DEFLATED
    }

    public ZipOutputStream createArchiveOutputStream(File destination) {
        try {
            ZipOutputStream outStream = new ZipOutputStream(destination)
            outStream.setUseZip64(Zip64Mode.Never)
            outStream.setMethod(compressedMethod)
            return outStream;
        } catch (Exception e) {
            String message = String.format("Unable to create ZIP output stream for file %s.", destination)
            throw new UncheckedIOException(message, e)
        }
    }
}
