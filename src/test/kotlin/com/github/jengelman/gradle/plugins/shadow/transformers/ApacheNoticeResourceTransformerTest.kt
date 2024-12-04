package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.fail
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformerParameterTests.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ApacheNoticeResourceTransformerParameterTests.java).
 */
class ApacheNoticeResourceTransformerTest : BaseTransformerTest<ApacheNoticeResourceTransformer>() {

  init {
    setupTurkishLocale()
  }

  @Test
  fun testCanTransformResource() {
    assertThat(transformer.canTransformResource(getFileElement("META-INF/NOTICE"))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement("META-INF/NOTICE.TXT"))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement("META-INF/Notice.txt"))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement("META-INF/NOTICE.md"))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement("META-INF/Notice.md"))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement("META-INF/MANIFEST.MF"))).isFalse()
  }

  @Test
  fun testNoParametersShouldNotThrowNullPointerWhenNoInput() {
    processAndFailOnNullPointer("")
  }

  @Test
  fun testNoParametersShouldNotThrowNullPointerWhenNoLinesOfInput() {
    processAndFailOnNullPointer("Some notice text")
  }

  @Test
  fun testNoParametersShouldNotThrowNullPointerWhenOneLineOfInput() {
    processAndFailOnNullPointer("Some notice text\n")
  }

  @Test
  fun testNoParametersShouldNotThrowNullPointerWhenTwoLinesOfInput() {
    processAndFailOnNullPointer("Some notice text\nSome notice text\n")
  }

  @Test
  fun testNoParametersShouldNotThrowNullPointerWhenLineStartsWithSlashSlash() {
    processAndFailOnNullPointer("Some notice text\n//Some notice text\n")
  }

  @Test
  fun testNoParametersShouldNotThrowNullPointerWhenLineIsSlashSlash() {
    processAndFailOnNullPointer("//\n")
  }

  @Test
  fun testNoParametersShouldNotThrowNullPointerWhenLineIsEmpty() {
    processAndFailOnNullPointer("\n")
  }

  private fun processAndFailOnNullPointer(noticeText: String) {
    try {
      transformer.transform(
        TransformerContext.builder()
          .path(NOTICE_RESOURCE)
          .inputStream(noticeText.byteInputStream())
          .build(),
      )
    } catch (ignored: NullPointerException) {
      fail("Null pointer should not be thrown when no parameters are set.")
    }
  }

  companion object {
    private const val NOTICE_RESOURCE = "META-INF/NOTICE"
  }
}
