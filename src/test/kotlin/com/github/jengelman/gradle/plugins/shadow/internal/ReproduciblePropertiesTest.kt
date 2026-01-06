package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.invariantEolString
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ReproduciblePropertiesTest {
  @ParameterizedTest
  @MethodSource("generalCharsetsProvider")
  fun emptyProperties(charset: Charset) {
    val output = ReproducibleProperties().writeToString(charset)

    assertThat(output).isEqualTo("")
  }

  @ParameterizedTest
  @MethodSource("generalCharsetsProvider")
  fun asciiProps(charset: Charset) {
    val output =
      ReproducibleProperties()
        .also { props ->
          props["key"] = "value"
          props["key2"] = "value2"
          props["a"] = "b"
          props["d"] = "e"
          props["0"] = "1"
          props["b"] = "c"
          props["c"] = "d"
          props["e"] = "f"
        }
        .writeToString(charset)

    assertThat(output)
      .isEqualTo(
        """
        |0=1
        |a=b
        |b=c
        |c=d
        |d=e
        |e=f
        |key=value
        |key2=value2
        |"""
          .trimMargin()
      )
  }

  @ParameterizedTest
  @MethodSource("utfCharsetsProvider")
  fun utfProps(charset: Charset) {
    val output =
      ReproducibleProperties()
        .also { props ->
          props["äöüß"] = "aouss"
          props["áèô"] = "aeo"
          props["€²³"] = "x"
          props["传傳磨宿说説"] = "b"
        }
        .writeToString(charset)

    assertThat(output)
      .isEqualTo(
        """
        |áèô=aeo
        |äöüß=aouss
        |€²³=x
        |传傳磨宿说説=b
        |"""
          .trimMargin()
      )
  }

  private companion object Companion {
    @JvmStatic
    fun generalCharsetsProvider() =
      listOf(StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII) + utfCharsetsProvider()

    @JvmStatic fun utfCharsetsProvider() = listOf(StandardCharsets.UTF_8, StandardCharsets.UTF_16)

    fun ReproducibleProperties.writeToString(charset: Charset): String {
      return ByteArrayOutputStream()
        .also { writeWithoutComments(charset, it) }
        .toString(charset.name())
        .invariantEolString
    }
  }
}
