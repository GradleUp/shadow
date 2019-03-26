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
    public static final String MANIFEST_FILE = "META-INF/MANIFEST.MF"

    private ManifestAppenderTransformer transformer

    @Before
    void setUp() {
        transformer = new ManifestAppenderTransformer()
    }

    @Test
    void testCanTransformResource() {
        transformer.attributes = [
            new Tuple2( 'Name', 'org/foo/bar/' ),
            new Tuple2( 'Sealed', true )
        ]

        assertTrue(transformer.canTransformResource(getFileElement(MANIFEST_FILE)))
        assertTrue(transformer.canTransformResource(getFileElement(MANIFEST_FILE.toLowerCase())))
    }

    @Test
    void testHasTransformedResource() {
        transformer.attributes = [
            new Tuple2( 'Tag', 'Something' )
        ]

        assertTrue(transformer.hasTransformedResource())
    }

    @Test
    void testHasNotTransformedResource() {
        assertFalse(transformer.hasTransformedResource())
    }

    @Test
    void testTransformation() {
        transformer.attributes = [
            new Tuple2( 'Name', 'org/foo/bar/' ),
            new Tuple2( 'Sealed', true ),
            new Tuple2( 'Name', 'com/example/' ),
            new Tuple2( 'Sealed', false )
        ]

        transformer.transform(new TransformerContext(MANIFEST_FILE, getResourceStream(MANIFEST_FILE), Collections.<Relocator>emptyList(), new ShadowStats()))

        def testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        try {
            transformer.modifyOutputStream(zipOutputStream, true)
        } finally {
            zipOutputStream.close()
        }

        def targetLines = readFrom(testableZipFile, MANIFEST_FILE)
        assertFalse(targetLines.isEmpty())
        assertTrue(targetLines.size() > 4)

        def trailer = targetLines.subList(targetLines.size() - 5, targetLines.size())
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
        def sourceLines = getResourceStream(MANIFEST_FILE).readLines()

        transformer.transform(new TransformerContext(MANIFEST_FILE, getResourceStream(MANIFEST_FILE), Collections.<Relocator>emptyList(), new ShadowStats()))

        def testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        try {
            transformer.modifyOutputStream(zipOutputStream, true)
        } finally {
            zipOutputStream.close()
        }
        def targetLines = readFrom(testableZipFile, MANIFEST_FILE)

        assertEquals(sourceLines, targetLines)
    }

    static List<String> readFrom(File jarFile, String resourceName) {
        def zip = new ZipFile(jarFile)
        try {
            def entry = zip.getEntry(resourceName)
            if (!entry) {
                return Collections.<String>emptyList()
            }
            return zip.getInputStream(entry).readLines()
        } finally {
            zip.close()
        }
    }

    InputStream getResourceStream(String resource) {
        this.class.getClassLoader().getResourceAsStream(resource)
    }
}
