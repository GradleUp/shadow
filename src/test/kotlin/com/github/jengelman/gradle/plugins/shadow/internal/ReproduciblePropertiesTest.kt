package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ReproduciblePropertiesTest {
  @ParameterizedTest
  @MethodSource("charsets")
  fun emptyProperties(charset: Charset) {
    val props = ReproducibleProperties()

    val str = props.writeToString(charset)

    assertThat(str).isEqualTo("")
  }

  @ParameterizedTest
  @MethodSource("charsets")
  fun someProperties(charset: Charset) {
    val props = ReproducibleProperties()
    props["key"] = "value"
    props["key2"] = "value2"
    props["a"] = "b"
    props["d"] = "e"
    props["0"] = "1"
    props["b"] = "c"
    props["c"] = "d"
    props["e"] = "f"

    val str = props.writeToString(charset)

    assertThat(str).isEqualTo(
      """
      0=1
      a=b
      b=c
      c=d
      d=e
      e=f
      key=value
      key2=value2

      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @MethodSource("charsetsUtf")
  fun utfChars(charset: Charset) {
    val props = ReproducibleProperties()
    props["äöüß"] = "aouss"
    props["áèô"] = "aeo"
    props["€²³"] = "x"
    props["传傳磨宿说説"] = "b"

    val str = props.writeToString(charset)

    assertThat(str).isEqualTo(
      """
      áèô=aeo
      äöüß=aouss
      €²³=x
      传傳磨宿说説=b

      """.trimIndent(),
    )
  }

  internal fun ReproducibleProperties.writeToString(charset: Charset): String {
    val buffer = ByteArrayOutputStream()
    writeWithoutComments(charset, buffer)
    return buffer.toString(charset.name()).replace(System.lineSeparator(), "\n")
  }

  private companion object {
    @JvmStatic
    fun charsets() = listOf(
      StandardCharsets.ISO_8859_1,
      StandardCharsets.UTF_8,
      StandardCharsets.US_ASCII,
      StandardCharsets.UTF_16,
    )

    @JvmStatic
    fun charsetsUtf() = listOf(
      StandardCharsets.UTF_8,
      StandardCharsets.UTF_16,
    )
  }
}
