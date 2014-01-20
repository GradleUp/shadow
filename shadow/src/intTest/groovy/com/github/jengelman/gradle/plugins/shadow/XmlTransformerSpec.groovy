package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.integration.TestFile
import com.github.jengelman.gradle.plugins.shadow.support.ShadowPluginIntegrationSpec
import com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer

class XmlTransformerSpec extends ShadowPluginIntegrationSpec {

    def "ignores dtd"() {
        given:
        TestFile resources = testDirectory.createDir('src/main/resources')
        TestFile xml = resources.createFile('test.xml')
        xml << '''<?xml version="1.0" encoding="ISO-8859-1"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-invalid.missing">
<html>
  <!-- The doctype references an invalid URI which would fail the parsing if the DTD would be resolved -->
</html>'''

        buildFile << """
import ${XmlAppendingTransformer.name}

shadow {
    artifactAttached = false
    transformer(XmlAppendingTransformer) {
        resource = 'text.xml'
    }
}
"""

        when:
        execute('shadowJar')

        then:
        buildSuccessful()

        and:
        assert shadowOutput.exists()

        and:
        contains(['test.xml'])
    }
}
