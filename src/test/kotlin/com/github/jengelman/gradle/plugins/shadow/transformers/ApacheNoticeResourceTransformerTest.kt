package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ApacheNoticeResourceTransformerTest.java).
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
  fun canTransformByPattern() {
    assertThat(transformer.canTransformResource("META-INF/NOTICE.txt")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/NOTICE.log")).isFalse()
    transformer.exclude("META-INF/NOTICE.txt")
    transformer.include("META-INF/NOTICE.*")
    assertThat(transformer.canTransformResource("META-INF/NOTICE.txt")).isFalse()
    assertThat(transformer.canTransformResource("META-INF/NOTICE.log")).isTrue()
  }

  @Test
  fun preamble1ShouldHaveATrailingSpace() {
    val baos = ByteArrayOutputStream()
    val zos = ZipOutputStream(baos)

    transformer.projectName.set("test-project")
    transformer.transform(context())
    transformer.modifyOutputStream(zos, false)
    zos.close()

    val zis = ZipInputStream(baos.toByteArray().inputStream())
    zis.nextEntry
    val output = zis.readAllBytes().toString(Charset.forName(transformer.charsetName.get()))

    assertThat(output).contains("in this case for test-project")
  }

  private companion object {
    const val NOTICE_RESOURCE = "META-INF/NOTICE"

    fun context(text: String = ""): TransformerContext {
      return TransformerContext(NOTICE_RESOURCE, text.byteInputStream())
    }
  }
}
