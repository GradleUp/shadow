package com.github.jengelman.gradle.plugins.shadow.testkit.util

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import org.gradle.api.UncheckedIOException
import org.gradle.internal.UncheckedException

object HashUtil {
  @JvmStatic
  fun createHash(file: File, algorithm: String): HashValue {
    try {
      return createHash(FileInputStream(file), algorithm)
    } catch (e: UncheckedIOException) {
      // Catch any unchecked io exceptions and add the file path for troubleshooting
      throw UncheckedIOException(
        String.format("Failed to create %s hash for file %s.", algorithm, file.absolutePath),
        e.cause!!,
      )
    } catch (e: FileNotFoundException) {
      throw UncheckedIOException(e)
    }
  }

  private fun createHash(inputStream: InputStream, algorithm: String): HashValue {
    val messageDigest: MessageDigest
    try {
      messageDigest = createMessageDigest(algorithm)
      val buffer = ByteArray(4096)
      inputStream.use {
        while (true) {
          val nread = it.read(buffer)
          if (nread < 0) {
            break
          }
          messageDigest.update(buffer, 0, nread)
        }
      }
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    }
    return HashValue(messageDigest.digest())
  }

  private fun createMessageDigest(algorithm: String): MessageDigest {
    try {
      return MessageDigest.getInstance(algorithm)
    } catch (e: NoSuchAlgorithmException) {
      throw UncheckedException.throwAsUncheckedException(e)
    }
  }
}
