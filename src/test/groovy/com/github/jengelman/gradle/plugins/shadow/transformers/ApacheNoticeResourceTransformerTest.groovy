package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Modified from org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformerParameterTests.java
 */
class ApacheNoticeResourceTransformerTest extends TransformerTestSupport<ApacheNoticeResourceTransformer> {

  private static final String NOTICE_RESOURCE = "META-INF/NOTICE"
  private static ShadowStats stats

  static {
    setupTurkishLocale()
  }

  @BeforeEach
  void setUp() {
    transformer = new ApacheNoticeResourceTransformer(objectFactory)
    stats = new ShadowStats()
  }

  @Test
  void testCanTransformResource() {
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/NOTICE")))
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/NOTICE.TXT")))
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/Notice.txt")))
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/NOTICE.md")))
    assertTrue(transformer.canTransformResource(getFileElement("META-INF/Notice.md")))
    assertFalse(transformer.canTransformResource(getFileElement("META-INF/MANIFEST.MF")))
  }

  @Test
  void testNoParametersShouldNotThrowNullPointerWhenNoInput() {
    processAndFailOnNullPointer("")
  }

  @Test
  void testNoParametersShouldNotThrowNullPointerWhenNoLinesOfInput() {
    processAndFailOnNullPointer("Some notice text")
  }

  @Test
  void testNoParametersShouldNotThrowNullPointerWhenOneLineOfInput() {
    processAndFailOnNullPointer("Some notice text\n")
  }

  @Test
  void testNoParametersShouldNotThrowNullPointerWhenTwoLinesOfInput() {
    processAndFailOnNullPointer("Some notice text\nSome notice text\n")
  }

  @Test
  void testNoParametersShouldNotThrowNullPointerWhenLineStartsWithSlashSlash() {
    processAndFailOnNullPointer("Some notice text\n//Some notice text\n")
  }

  @Test
  void testNoParametersShouldNotThrowNullPointerWhenLineIsSlashSlash() {
    processAndFailOnNullPointer("//\n")
  }

  @Test
  void testNoParametersShouldNotThrowNullPointerWhenLineIsEmpty() {
    processAndFailOnNullPointer("\n")
  }

  private static void processAndFailOnNullPointer(final String noticeText) {
    try {
      final ByteArrayInputStream noticeInputStream = new ByteArrayInputStream(noticeText.getBytes())
      final List<Relocator> emptyList = Collections.emptyList()
      transformer.transform(TransformerContext.builder().path(NOTICE_RESOURCE).inputStream(noticeInputStream).relocators(emptyList).stats(stats).build())
    }
    catch (NullPointerException ignored) {
      fail("Null pointer should not be thrown when no parameters are set.")
    }
  }
}
