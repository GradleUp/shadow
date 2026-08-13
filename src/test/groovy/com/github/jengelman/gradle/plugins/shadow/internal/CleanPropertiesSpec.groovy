package com.github.jengelman.gradle.plugins.shadow.internal

import spock.lang.Specification
import java.nio.charset.StandardCharsets

class CleanPropertiesSpec extends Specification {

    def "writes sorted properties without comments"() {
        given:
        CleanProperties props = new CleanProperties()
        props.put("key", "value")
        props.put("key2", "value2")
        props.put("a", "b")
        props.put("0", "1")

        ByteArrayOutputStream os = new ByteArrayOutputStream()

        when:
        props.writeWithoutComments(StandardCharsets.UTF_8, os)

        then:
        String result = os.toString("UTF-8").replace("\r\n", "\n")
        result == "0=1\na=b\nkey=value\nkey2=value2\n"
    }
}
