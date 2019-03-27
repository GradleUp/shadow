package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.apache.tools.zip.ZipOutputStream
import org.junit.Before
import org.junit.Test

import java.util.zip.ZipFile

import static java.util.Arrays.asList
import static org.junit.Assert.*

class ManifestAppenderTransformerTest extends TransformerTestSupport {
    static final String MANIFEST_NAME = "META-INF/MANIFEST.MF"

    private ManifestAppenderTransformer transformer

    @Before
    void setUp() {
        transformer = new ManifestAppenderTransformer()
    }

    @Test
    void testCanTransformResource() {
        transformer.attribute('Name', 'org/foo/bar/')
        transformer.attribute('Sealed', true)

        assertTrue(transformer.canTransformResource(getFileElement(MANIFEST_NAME)))
        assertTrue(transformer.canTransformResource(getFileElement(MANIFEST_NAME.toLowerCase())))
    }

    @Test
    void testHasTransformedResource() {
        transformer.attribute('Tag', 'Something')

        assertTrue(transformer.hasTransformedResource())
    }

    @Test
    void testHasNotTransformedResource() {
        assertFalse(transformer.hasTransformedResource())
    }

    @Test
    void testTransformation() {
        transformer.attribute('Name', 'org/foo/bar/')
        transformer.attribute('Sealed', true)
        transformer.attribute('Name', 'com/example/')
        transformer.attribute('Sealed', false)

        transformer.transform(new TransformerContext(MANIFEST_NAME, getResourceStream(MANIFEST_NAME), Collections.<Relocator>emptyList(), new ShadowStats()))

        def testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        try {
            transformer.modifyOutputStream(zipOutputStream, true)
        } finally {
            zipOutputStream.close()
        }

        def targetLines = readFrom(testableZipFile, MANIFEST_NAME)
        assertFalse(targetLines.isEmpty())
        assertTrue(targetLines.size() > 4)

        def trailer = targetLines.with { subList(size() - 5, size()) }
        assertEquals(asList(
            "Name: org/foo/bar/",
            "Sealed: true",
            "Name: com/example/",
            "Sealed: false",
            ""), trailer
        )
    }

    @Test
    void testNoTransformation() {
        def sourceLines = getResourceStream(MANIFEST_NAME).readLines()

        transformer.transform(new TransformerContext(MANIFEST_NAME, getResourceStream(MANIFEST_NAME), Collections.<Relocator>emptyList(), new ShadowStats()))

        def testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        try {
            transformer.modifyOutputStream(zipOutputStream, true)
        } finally {
            zipOutputStream.close()
        }
        def targetLines = readFrom(testableZipFile, MANIFEST_NAME)

        assertEquals(sourceLines, targetLines)
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
