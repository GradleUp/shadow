package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import org.apache.tools.zip.ZipOutputStream
import org.junit.Before
import org.junit.Test

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static java.util.Collections.singletonList
import static org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE

/**
 * @author Paul Nelson Baker
 * @since 2018-08
 * @see <a href="https://github.com/paul-nelson-baker/">GitHub</a>
 * @see <a href="https://www.linkedin.com/in/paul-n-baker/">LinkedIn</a>
 */
//@RunWith(Parameterized.class)
class Log4j2PluginsCacheFileTransformerTest {

    Log4j2PluginsCacheFileTransformer transformer

    @Before
    void setUp() {
        transformer = new Log4j2PluginsCacheFileTransformer()
    }

    @Test
    void testShouldNotTransform() {
        transformer.transform(new TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(PLUGIN_CACHE_FILE), null))
        assertFalse(transformer.hasTransformedResource())
    }

    @Test
    void testShouldTransform() {
        List<Relocator> relocators = new ArrayList<>()
        relocators.add(new SimpleRelocator(null, null, null, null))
        transformer.transform(new TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(PLUGIN_CACHE_FILE), relocators))
        assertTrue(transformer.hasTransformedResource())
    }

    @Test
    void testRelocators() {
        testRelocate("org.apache.logging", "new.location.org.apache.logging", "new.location.org.apache.logging")
        testRelocate("org.apache.logging", "new.location.org.apache.logging", "org.apache.logging")
    }

    void testRelocate(String source, String pattern, String target) throws IOException {
        List<Relocator> relocators = singletonList((Relocator) new SimpleRelocator(source, pattern, null, null))
        transformer.transform(new TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(PLUGIN_CACHE_FILE), relocators))
        assertTrue(transformer.hasTransformedResource(), "Transformer didn't transform resources")
        // Write out to a fake jar file
        File.createTempFile("testable-zip-file-", ".jar").withCloseable { testableZipFile ->
            new FileOutputStream(testableZipFile).withCloseable { fileOutputStream ->
                new BufferedOutputStream(fileOutputStream).withCloseable { bufferedOutputStream ->
                    new ZipOutputStream(bufferedOutputStream).withCloseable { zipOutputStream ->
                        transformer.modifyOutputStream(zipOutputStream)
                    }
                }
            }
        }
        // Pull the data back out and make sure it was transformed
        ZipFile zipFile = new ZipFile(testableZipFile)
        ZipEntry zipFileEntry = zipFile.getEntry(PLUGIN_CACHE_FILE)
        InputStream inputStream = zipFile.getInputStream(zipFileEntry)
        new Scanner(inputStream).withReader { scanner ->
            boolean hasAtLeastOneTransform = false
            while (scanner.hasNextLine()) {
                String nextLine = scanner.nextLine()
                if (nextLine.contains(source)) {
                    hasAtLeastOneTransform = true
                    assertTrue(nextLine.contains(target), "Target wasn't included in transform")
                }
            }
            assertTrue(hasAtLeastOneTransform, "There were no transformations inside the file")
        }
    }
}
