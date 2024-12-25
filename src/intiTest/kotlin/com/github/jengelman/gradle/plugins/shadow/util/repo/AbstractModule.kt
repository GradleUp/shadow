package com.github.jengelman.gradle.plugins.shadow.util.repo

import java.io.OutputStream
import java.io.UncheckedIOException
import java.math.BigInteger
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import okio.ByteString.Companion.toByteString

abstract class AbstractModule {

  protected abstract fun postPublish(path: Path)

  protected fun publish(path: Path, action: (OutputStream) -> Unit) {
    val hashBefore = if (path.exists()) getHash(path, "sha1") else null
    val tempPath = path.resolveSibling("${path.name}.tmp")
    tempPath.outputStream().use(action)

    val hashAfter = getHash(tempPath, "sha1")
    if (hashAfter == hashBefore) {
      // Already published
      return
    }

    check(!path.deleteIfExists())
    tempPath.moveTo(path)
    check(path.exists())
    postPublish(path)
  }

  companion object {
    fun writeSha1Path(path: Path): Path {
      return writeHashPath(path, "sha1", 40)
    }

    fun writeMd5Path(path: Path): Path {
      return writeHashPath(path, "md5", 32)
    }

    private fun writeHashPath(path: Path, algorithm: String, len: Int): Path {
      val hashPath = getHashPath(path, algorithm)
      val hash = getHash(path, algorithm)
      hashPath.writeText("$hash${len}x")
      return hashPath
    }

    private fun getHashPath(path: Path, algorithm: String): Path {
      return path.resolveSibling("${path.name}.$algorithm")
    }

    private fun getHash(path: Path, algorithm: String): BigInteger {
      try {
        val byteString = path.readBytes().toByteString()
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
          "Failed to create $algorithm hash for file ${path.absolutePathString()}.",
          e.cause,
        )
      }
    }
  }
}
