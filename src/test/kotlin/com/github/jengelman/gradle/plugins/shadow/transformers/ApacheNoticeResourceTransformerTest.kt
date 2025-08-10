package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.fail
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformerParameterTests.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ApacheNoticeResourceTransformerParameterTests.java).
 */
class ApacheNoticeResourceTransformerTest : BaseTransformerTest<ApacheNoticeResourceTransformer>() {

  init {
    setupTurkishLocale()
  }

  @Test
  fun canTransformResource() {
    assertThat(transformer.canTransformResource("META-INF/NOTICE")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/NOTICE.TXT")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/Notice.txt")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/NOTICE.md")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/Notice.md")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/MANIFEST.MF")).isFalse()
  }

  @Test
  fun preamble1ShouldHaveATrailingSpace() {
    val baos = ByteArrayOutputStream()
    val zos = ZipOutputStream(baos)

    transformer.projectName.set("test-project")
    transformer.transform(
      TransformerContext(
        path = NOTICE_RESOURCE,
        inputStream = "".byteInputStream(),
      ),
    )
    transformer.modifyOutputStream(zos, false)
    zos.close()

    val zis = ZipInputStream(ByteArrayInputStream(baos.toByteArray()))
    zis.nextEntry
    val output = zis.readAllBytes().toString(Charset.forName(transformer.charsetName.get()))

    assertThat(output).contains("in this case for test-project")
  }

  @Test
  fun noParametersShouldNotThrowNullPointerWhenNoInput() {
    processAndFailOnNullPointer("")
  }

  @Test
  fun noParametersShouldNotThrowNullPointerWhenNoLinesOfInput() {
    processAndFailOnNullPointer("Some notice text")
  }

  @Test
  fun noParametersShouldNotThrowNullPointerWhenOneLineOfInput() {
    processAndFailOnNullPointer("Some notice text\n")
  }

  @Test
  fun noParametersShouldNotThrowNullPointerWhenTwoLinesOfInput() {
    processAndFailOnNullPointer("Some notice text\nSome notice text\n")
  }

  @Test
  fun noParametersShouldNotThrowNullPointerWhenLineStartsWithSlashSlash() {
    processAndFailOnNullPointer("Some notice text\n//Some notice text\n")
  }

  @Test
  fun noParametersShouldNotThrowNullPointerWhenLineIsSlashSlash() {
    processAndFailOnNullPointer("//\n")
  }

  @Test
  fun noParametersShouldNotThrowNullPointerWhenLineIsEmpty() {
    processAndFailOnNullPointer("\n")
  }

  private fun processAndFailOnNullPointer(noticeText: String) {
    try {
      transformer.transform(
        TransformerContext(
          path = NOTICE_RESOURCE,
          inputStream = noticeText.byteInputStream(),
        ),
      )
    } catch (_: NullPointerException) {
      fail("Null pointer should not be thrown when no parameters are set.")
    }
  }

  companion object {
    private const val NOTICE_RESOURCE = "META-INF/NOTICE"
  }
}
