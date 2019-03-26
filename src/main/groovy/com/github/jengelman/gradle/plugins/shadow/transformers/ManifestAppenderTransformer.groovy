package com.github.jengelman.gradle.plugins.shadow.transformers

import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.codehaus.plexus.util.IOUtil
import org.gradle.api.file.FileTreeElement

import static java.nio.charset.StandardCharsets.*
import static java.util.jar.JarFile.*

class ManifestAppenderTransformer implements Transformer {
    private static final byte[] EOL = "\r\n".getBytes(UTF_8)
    private static final byte[] SEPARATOR = ": ".getBytes(UTF_8)

    private byte[] manifestContents

    List<Tuple2<String, ? extends Comparable<?>>> attributes = []

    @Override
    boolean canTransformResource(FileTreeElement element) {
        MANIFEST_NAME.equalsIgnoreCase(element.relativePath.pathString)
    }

    @Override
    void transform(TransformerContext context) {
        manifestContents = IOUtil.toByteArray(context.is)
        IOUtil.close(context.is)
    }

    @Override
    boolean hasTransformedResource() {
        !attributes.isEmpty()
    }

    @Override
    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        ZipEntry entry = new ZipEntry(MANIFEST_NAME)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)
        os.write(manifestContents)

        if (!attributes.isEmpty()) {
            for (attribute in attributes) {
                os.write(attribute.first.getBytes(UTF_8))
                os.write(SEPARATOR)
                os.write(attribute.second.toString().getBytes(UTF_8))
                os.write(EOL)
            }
            os.write(EOL)
            attributes.clear()
        }
    }
}
