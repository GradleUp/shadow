package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.GradleException
import spock.lang.Specification

class ZipEntryValidationSpec extends Specification {

    def "valid zip entry names do not throw"() {
        expect:
        ZipUtils.zipEntry(name).name == name

        where:
        name << [
            "com/example/MyClass.class",
            "META-INF/MANIFEST.MF",
            "assets/icon..png",
            "foo/bar/baz.txt",
            "relative/path/to/resource"
        ]
    }

    def "malicious zip entry names with path traversal throw exception"() {
        when:
        ZipUtils.zipEntry(name)

        then:
        def e = thrown(GradleException)
        e.message == "Malicious ZIP entry containing path traversal sequence: $name"

        where:
        name << [
            "../../../../tmp/pwned.txt",
            "foo/../../bar",
            "foo\\..\\..\\bar",
            "..",
            "../file.txt",
            "..\\file.txt",
            "foo/bar/..",
            "foo/bar/../baz"
        ]
    }
}
