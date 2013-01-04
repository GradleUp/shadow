package org.gradle.api.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.mvn3.org.codehaus.plexus.util.IOUtil

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class ServiceFileTransformer implements Transformer {

    private static final String SERVICES_PATH = "META-INF/services";
    Map<RelativePath, ServiceStream> serviceEntries = [:]

    @Override
    boolean canTransformResource(FileTreeElement entry) {
        return entry.relativePath.pathString.contains(SERVICES_PATH)
    }

    @Override
    void transform(FileTreeElement entry, JarFile jar, JarOutputStream jos) {
        ServiceStream out = serviceEntries[entry.relativePath]
        if ( out == null ) {
            out = new ServiceStream()
            serviceEntries[entry.relativePath] = out
        }
        JarEntry je = jar.getJarEntry(entry.relativePath.pathString)
        out.append(jar.getInputStream(je))
    }

    @Override
    void modifyOutputStream(JarOutputStream jos) {
        serviceEntries.each { RelativePath path, ServiceStream stream ->
            jos.putNextEntry(new JarEntry(path.pathString))
            IOUtil.copy(stream.toInputStream(), jos)
            jos.closeEntry()
        }
    }

    static class ServiceStream extends ByteArrayOutputStream{

        public ServiceStream(){
            super( 1024 );
        }

        public void append( InputStream is ) throws IOException {
            if ( count > 0 && buf[count - 1] != '\n' && buf[count - 1] != '\r' ) {
                byte[] newline = '\n'.bytes;
                write(newline, 0, newline.length);
            }
            IOUtil.copy(is, this);
        }

        public InputStream toInputStream() {
            return new ByteArrayInputStream( buf, 0, count );
        }
    }
}
