package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.tools.zip.ZipOutputStream
import spock.lang.Unroll

import java.util.jar.JarInputStream

import static java.util.Collections.singletonList
import static org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE

/**
 * Modified from <a href="https://github.com/apache/logging-log4j-transform/blob/main/log4j-transform-maven-shade-plugin-extensions/src/test/java/org/apache/logging/log4j/maven/plugins/shade/transformer/Log4j2PluginCacheFileTransformerTest.java">Log4j2PluginCacheFileTransformerTest.java</a>.
 */
class Log4j2PluginsCacheFileTransformerSpec extends TransformerSpecSupport {

    Log4j2PluginsCacheFileTransformer transformer

    void setup() {
        transformer = new Log4j2PluginsCacheFileTransformer()
    }

    void "canTransformResource"() {
        expect:
        !transformer.canTransformResource(getFileElement(""))
        !transformer.canTransformResource(getFileElement("."))
        !transformer.canTransformResource(getFileElement("tmp.dat"))
        !transformer.canTransformResource(getFileElement("${PLUGIN_CACHE_FILE}.tmp"))
        !transformer.canTransformResource(getFileElement("tmp/${PLUGIN_CACHE_FILE}"))
        transformer.canTransformResource(getFileElement(PLUGIN_CACHE_FILE))
    }

    void "transformAndModifyOutputStream"() {
        expect:
        !transformer.hasTransformedResource()

        when:
        transformer.transform(new TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(PLUGIN_CACHE_FILE), null))
        transformer.transform(new TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(PLUGIN_CACHE_FILE), null))

        then:
        transformer.hasTransformedResource()

        when:
        def jarBuff = new ByteArrayOutputStream()
        new ZipOutputStream(jarBuff).withCloseable { zos ->
            transformer.modifyOutputStream(zos, false)
        }
        def foundEntry = findEntry(jarBuff)

        then:
        foundEntry != null
    }

    private static String findEntry(ByteArrayOutputStream jarBuff) {
        new JarInputStream(new ByteArrayInputStream(jarBuff.toByteArray())).withCloseable { jarIn ->
            def entry = jarIn.nextJarEntry
            while (entry != null) {
                if (entry.name == PLUGIN_CACHE_FILE) {
                    return entry.name
                }
                entry = jarIn.nextJarEntry
            }
            return null
        }
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

    @Unroll
    void "relocations [#pattern -> #shadedPattern expects #target]"() {
        given:
        PluginCache aggregator = new PluginCache()
        aggregator.loadCacheFiles(Collections.enumeration([getResourceUrl(PLUGIN_CACHE_FILE)]))
        transformer.transform(new TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(PLUGIN_CACHE_FILE), [new SimpleRelocator(pattern, shadedPattern, null, null)], new ShadowStats()))
        transformer.relocatePlugins(aggregator)

        expect:
        for (def pluginEntryMap : aggregator.allCategories.values()) {
            for (def entry : pluginEntryMap.values()) {
                assert entry.className.startsWith(target)
            }
        }

        where:
        pattern               | shadedPattern                         | target
        "org.apache.logging"  | "new.location.org.apache.logging"     | "new.location.org.apache.logging"
        "com.apache.logging"  | "new.location.com.apache.logging"     | "org.apache.logging"
    }

    InputStream getResourceStream(String resource) {
        return this.class.getClassLoader().getResourceAsStream(resource)
    }

    URL getResourceUrl(String resource) {
        return this.class.getClassLoader().getResource(resource)
    }
}
