package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.apache.tools.zip.ZipOutputStream
import org.junit.Before
import org.junit.Test

import java.util.zip.ZipFile

import static java.util.Arrays.asList
import static org.junit.Assert.*

/**
 * Test for {@link PropertiesFileTransformer}.
 */
final class PropertiesFileTransformerTest extends TransformerTestSupport {
    static final String MANIFEST_NAME = "META-INF/MANIFEST.MF"

    private PropertiesFileTransformer transformer

    @Before
    void setUp() {
        transformer = new PropertiesFileTransformer()
    }

    @Test
    void testHasTransformedResource() {
        transformer.transform(new TransformerContext(MANIFEST_NAME))

        assertTrue(transformer.hasTransformedResource())
    }

    @Test
    void testHasNotTransformedResource() {
        assertFalse(transformer.hasTransformedResource())
    }

    @Test
    void testTransformation() {
        transformer.transform(new TransformerContext(MANIFEST_NAME, getResourceStream(MANIFEST_NAME), Collections.<Relocator>emptyList(), new ShadowStats()))

        def testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        try {
            transformer.modifyOutputStream(zipOutputStream, false)
        } finally {
            zipOutputStream.close()
        }
        def targetLines = readFrom(testableZipFile, MANIFEST_NAME)

        assertFalse(targetLines.isEmpty())

        assertTrue(targetLines.contains("Manifest-Version=1.0"))
    }

    static List<String> readFrom(File jarFile, String resourceName) {
        def zip = new ZipFile(jarFile)
        try {
            def entry = zip.getEntry(resourceName)
            if (!entry) {
                return Collections.emptyList()
            }
            return zip.getInputStream(entry).readLines()
        } finally {
            zip.close()
        }
    }

    InputStream getResourceStream(String resource) {
        this.class.classLoader.getResourceAsStream(resource)
    }
}
