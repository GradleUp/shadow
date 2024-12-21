package com.github.jengelman.gradle.plugins.shadow.util.repo

import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.io.UncheckedIOException
import java.math.BigInteger
import okio.ByteString.Companion.toByteString

abstract class AbstractModule {

  protected abstract fun onPublish(file: File)

  protected fun publish(file: File, action: (OutputStream) -> Unit) {
    val hashBefore = if (file.exists()) getHash(file, "sha1") else null
    val tempFile = file.resolveSibling("${file.name}.tmp")
    tempFile.outputStream().use(action)

    val hashAfter = getHash(tempFile, "sha1")
    if (hashAfter == hashBefore) {
      // Already published
      return
    }

    check(!file.exists() || file.delete())
    check(tempFile.renameTo(file))
    onPublish(file)
  }

  companion object {
    fun sha1File(file: File): File {
      return hashFile(file, "sha1", 40)
    }

    fun md5File(file: File): File {
      return hashFile(file, "md5", 32)
    }

    private fun hashFile(file: File, algorithm: String, len: Int): File {
      val hashFile = getHashFile(file, algorithm)
      val hash = getHash(file, algorithm)
      hashFile.writeText("$hash${len}x")
      return hashFile
    }

    private fun getHashFile(file: File, algorithm: String): File {
      return file.resolveSibling("${file.name}.$algorithm")
    }

    private fun getHash(file: File, algorithm: String): BigInteger {
      try {
        val byteString = file.readBytes().toByteString()
        val byteArray = when (algorithm.uppercase()) {
          "MD5" -> byteString.md5()
          "SHA1" -> byteString.sha1()
          "SHA256" -> byteString.sha256()
          "SHA512" -> byteString.sha512()
          else -> throw IllegalArgumentException("Unsupported algorithm: $algorithm")
        }.toByteArray()
        return BigInteger(1, byteArray)
      } catch (e: UncheckedIOException) {
        // Catch any unchecked io exceptions and add the file path for troubleshooting
        throw UncheckedIOException(
          "Failed to create $algorithm hash for file ${file.absolutePath}.",
          e.cause,
        )
      } catch (e: FileNotFoundException) {
        throw UncheckedIOException(e)
      }
    }
  }
}
