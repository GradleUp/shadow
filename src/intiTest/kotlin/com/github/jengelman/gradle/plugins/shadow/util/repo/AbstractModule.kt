package com.github.jengelman.gradle.plugins.shadow.util.repo

import com.github.jengelman.gradle.plugins.shadow.util.HashUtil
import java.io.File
import java.io.OutputStream
import java.io.Writer
import java.math.BigInteger

abstract class AbstractModule {

  protected abstract fun onPublish(file: File)

  protected fun publishWithWriter(file: File, action: (Writer) -> Unit) {
    publishCommon(file) { it.writer().use(action) }
  }

  protected fun publishWithStream(file: File, action: (OutputStream) -> Unit) {
    publishCommon(file) { it.outputStream().use(action) }
  }

  private fun publishCommon(file: File, action: (File) -> Unit) {
    val hashBefore = if (file.exists()) getHash(file, "sha1") else null
    val tempFile = file.resolveSibling("${file.name}.tmp")
    action(tempFile)

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
    @JvmStatic
    fun sha1File(file: File): File {
      return hashFile(file, "sha1", 40)
    }

    @JvmStatic
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
      return File(file.parentFile, "${file.name}.$algorithm")
    }

    private fun getHash(file: File, algorithm: String): BigInteger {
      return HashUtil.createHash(file, algorithm.uppercase()).digest
    }
  }
}
