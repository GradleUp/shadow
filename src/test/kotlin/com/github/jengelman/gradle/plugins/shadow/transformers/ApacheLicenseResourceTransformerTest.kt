package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheLicenseResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ApacheLicenseResourceTransformerTest.java).
 */
class ApacheLicenseResourceTransformerTest : BaseTransformerTest<ApacheLicenseResourceTransformer>() {

  init {
    setupTurkishLocale()
  }

  @Test
  fun canTransformResource() {
    assertThat(transformer.canTransformResource("META-INF/LICENSE")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/LICENSE.TXT")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/License.txt")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/LICENSE.md")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/License.md")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/MANIFEST.MF")).isFalse()
  }

  @Test
  fun licensesAreIncluded() {
    val baos = ByteArrayOutputStream()
    val zos = ZipOutputStream(baos)

    transformer.separator.set("\n---\n")
    transformer.transform(TransformerContext("META-INF/LICENSE", "License 1".byteInputStream()))
    transformer.transform(TransformerContext("META-INF/LICENSE.txt", "License 2".byteInputStream()))
    transformer.transform(TransformerContext("META-INF/LICENSE.md", "License 3".byteInputStream()))
    transformer.transform(TransformerContext("META-INF/LICENSE.txt", "License 1".byteInputStream()))
    transformer.transform(TransformerContext("META-INF/LICENSE.txt", "\n\r\nLicense 2".byteInputStream()))
    transformer.transform(TransformerContext("META-INF/LICENSE.txt", "\n\r\nLicense 2\n\r\n".byteInputStream()))
    transformer.modifyOutputStream(zos, false)
    zos.close()

    val zis = ZipInputStream(baos.toByteArray().inputStream())
    zis.nextEntry
    val output = zis.readAllBytes().toString(Charset.forName(transformer.charsetName.get()))

    assertThat(output).isEqualTo(
      """
      License 1
      ---
      License 2
      ---
      License 3
      """.trimIndent(),
    )
  }
}
