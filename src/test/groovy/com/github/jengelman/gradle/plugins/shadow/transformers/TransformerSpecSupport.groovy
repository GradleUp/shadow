package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFileTreeElement
import spock.lang.Shared
import spock.lang.Specification

class TransformerSpecSupport extends Specification {

    @Shared
    ShadowStats stats

    def setup() {
        stats = new ShadowStats()
    }

    protected static FileTreeElement getFileElement(String path) {
        return new DefaultFileTreeElement(null, RelativePath.parse(true, path), null, null)
    }

    protected static InputStream toInputStream(String str) {
        return new ByteArrayInputStream(str.bytes)
    }

    protected static InputStream toInputStream(Properties props) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        props.store(baos, '')
        new ByteArrayInputStream(baos.toByteArray())
    }

    protected static Properties toProperties(Map map) {
        map.inject(new Properties()) { Properties props, entry ->
            props.put(entry.key, entry.value)
            props
        }
    }

    protected static Map toMap(Properties props) {
        props.inject([:]) { Map map, entry ->
            map.put(entry.key, entry.value)
            map
        }
    }

    protected TransformerContext context(String path, Map input) {
        TransformerContext.builder().path(path).is(toInputStream(toProperties(input))).relocators([]).stats(stats).build()
    }

    protected TransformerContext context(String path, String input) {
        TransformerContext.builder().path(path).is(toInputStream(input)).relocators([]).stats(stats).build()
    }

}
