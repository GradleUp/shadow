package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.invariantEolString
import de.infix.testBalloon.framework.core.testSuite
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

val ReproduciblePropertiesTests by testSuite {
  val subject = ReproduciblePropertiesTest()

  for (charset in ReproduciblePropertiesTest.generalCharsetsProvider) {
    test("emptyProperties_${charset.name()}") { subject.emptyProperties(charset) }
    test("asciiProps_${charset.name()}") { subject.asciiProps(charset) }
    test("escapesSpecialCharacters_${charset.name()}") { subject.escapesSpecialCharacters(charset) }
  }

  for (charset in ReproduciblePropertiesTest.utfCharsetsProvider) {
    test("utfProps_${charset.name()}") { subject.utfProps(charset) }
  }
}

private class ReproduciblePropertiesTest {
  fun emptyProperties(charset: Charset) {
    val output = ReproducibleProperties().writeToString(charset)

    assertThat(output).isEqualTo("")
  }

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

  fun escapesSpecialCharacters(charset: Charset) {
    val output =
      ReproducibleProperties()
        .also { properties ->
          properties[" leading:=#!"] = "line1\nline2\t\\"
        }
        .writeToString(charset)

    assertThat(output)
      .isEqualTo(
        """
        |\ leading\:\=\#\!=line1\nline2\t\\
        |"""
          .trimMargin()
      )
  }

  companion object Companion {
    val utfCharsetsProvider = listOf(Charsets.UTF_8, Charsets.UTF_16)

    val generalCharsetsProvider =
      listOf(Charsets.ISO_8859_1, Charsets.US_ASCII) + utfCharsetsProvider

    internal fun ReproducibleProperties.writeToString(charset: Charset): String {
      return ByteArrayOutputStream()
        .also { writeWithoutComments(charset, it) }
        .toString(charset.name())
        .invariantEolString
    }
  }
}
