package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.tools.zip.ZipOutputStream
import spock.lang.Specification


import static java.util.Collections.singletonList
import static org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE

/**
 * @author Paul Nelson Baker
 * @since 2018-08
 * @see <a href="https://github.com/paul-nelson-baker/">GitHub</a>
 * @see <a href="https://www.linkedin.com/in/paul-n-baker/">LinkedIn</a>
 */
class Log4j2PluginsCacheFileTransformerSpec extends Specification{

    Log4j2PluginsCacheFileTransformer transformer

    void setup() {
        transformer = new Log4j2PluginsCacheFileTransformer()
    }

    void "should not transformer"() {
        when:
        transformer.transform(new TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(PLUGIN_CACHE_FILE), null))

        then:
        !transformer.hasTransformedResource()
    }

    void "should transform"() {
        given:
        List<Relocator> relocators = new ArrayList<>()
        relocators.add(new SimpleRelocator(null, null, null, null))

        when:
        transformer.transform(new TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(PLUGIN_CACHE_FILE), relocators))

        then:
        transformer.hasTransformedResource()
    }

    void "relocate classes inside DAT file"() {
        given:
        String pattern = "org.apache.logging"
        String destination = "new.location.org.apache.logging"

        List<Relocator> relocators = singletonList((Relocator) new SimpleRelocator(pattern, destination, null, null))

        when:
        transformer.transform(new TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(PLUGIN_CACHE_FILE), relocators, new ShadowStats()))

        then:
        transformer.hasTransformedResource()

        when:
        // Write out to a fake jar file
        def testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        transformer.modifyOutputStream(zipOutputStream, true)

        zipOutputStream.close()
        bufferedOutputStream.close()
        fileOutputStream.close()

        then:
        // Pull the data back out and make sure it was transformed
        PluginCache cache = new PluginCache()
        def urlString = "jar:" + testableZipFile.toURI().toURL() + "!/" + PLUGIN_CACHE_FILE
        cache.loadCacheFiles(Collections.enumeration([new URL(urlString)]))

        cache.getCategory("lookup")["date"].className == "new.location.org.apache.logging.log4j.core.lookup.DateLookup"

    }

    InputStream getResourceStream(String resource) {
        return this.class.getClassLoader().getResourceAsStream(resource)
    }
}
