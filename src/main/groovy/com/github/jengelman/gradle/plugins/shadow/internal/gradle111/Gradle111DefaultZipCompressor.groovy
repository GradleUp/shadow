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
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(destination))
            ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)
            zipOutputStream.setUseZip64(Zip64Mode.Never)
            zipOutputStream.setMethod(compressedMethod)
            return zipOutputStream;
        } catch (Exception e) {
            String message = String.format("Unable to create ZIP output stream for file %s.", destination)
            throw new UncheckedIOException(message, e)
        }
    }
}
