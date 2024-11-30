package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.zip.ZipFile

import static java.util.Arrays.asList
import static org.junit.jupiter.api.Assertions.*

/**
 * Test for {@link ManifestAppenderTransformer}.
 */
class ManifestAppenderTransformerTest extends TransformerTestSupport<ManifestAppenderTransformer> {

  @BeforeEach
  void setUp() {
    transformer = new ManifestAppenderTransformer(objectFactory)
  }

  @Test
  void testCanTransformResource() {
    transformer.with {
      append('Name', 'org/foo/bar/')
      append('Sealed', true)
    }

    assertTrue(transformer.canTransformResource(getFileElement(MANIFEST_NAME)))
    assertTrue(transformer.canTransformResource(getFileElement(MANIFEST_NAME.toLowerCase())))
  }

  @Test
  void testHasTransformedResource() {
    transformer.append('Tag', 'Something')

    assertTrue(transformer.hasTransformedResource())
  }

  @Test
  void testHasNotTransformedResource() {
    assertFalse(transformer.hasTransformedResource())
  }

  @Test
  void testTransformation() {
    transformer.with {
      append('Name', 'org/foo/bar/')
      append('Sealed', true)
      append('Name', 'com/example/')
      append('Sealed', false)

      transform(new TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME)))
    }

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
    def sourceLines = requireResourceAsStream(MANIFEST_NAME).readLines()

    transformer.transform(new TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME)))

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
}
