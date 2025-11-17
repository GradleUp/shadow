package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.OutputStream
import java.nio.charset.Charset
import java.util.HexFormat
import java.util.SortedMap

/**
 * Maintains a map of properties sorted by key, which can be written out to a text file with a consistent
 * ordering to satisfy the requirements of reproducible builds.
 */
internal class ReproducibleProperties {
  internal val props: SortedMap<String, String> = sortedMapOf()

  fun writeProperties(charset: Charset, os: OutputStream, escape: Boolean) {
    val zipWriter = os.writer(charset)
    props.forEach { (key, value) ->
      zipWriter.write(convertString(key, isKey = true, escape))
      zipWriter.write("=")
      zipWriter.write(convertString(value, isKey = false, escape))
      zipWriter.write("\n")
    }
    zipWriter.flush()
  }

  private fun convertString(
    str: String,
    isKey: Boolean,
    escape: Boolean,
  ): String {
    val len = str.length
    val out = StringBuilder()
    val hex = HexFormat.of().withUpperCase()
    for (x in 0..<len) {
      val aChar = str[x]
      // Handle the common case first, avoid more expensive special cases
      if ((aChar.code > 61) && (aChar.code < 127)) {
        out.append(if (aChar == '\\') "\\\\" else aChar)
        continue
      }
      when (aChar) {
        ' ' -> {
          if (x == 0 || isKey) out.append('\\')
          out.append(' ')
        }
        '\t' -> out.append("\\t")
        '\n' -> out.append("\\n")
        '\r' -> out.append("\\r")
        '\u000c' -> out.append("\\f")
        '=', ':', '#', '!' -> out.append('\\').append(aChar)
        else -> if (escape && ((aChar.code < 0x0020) || (aChar.code > 0x007e))) {
          out.append("\\u").append(hex.toHexDigits(aChar))
        } else {
          out.append(aChar)
        }
      }
    }
    return out.toString()
  }
}
