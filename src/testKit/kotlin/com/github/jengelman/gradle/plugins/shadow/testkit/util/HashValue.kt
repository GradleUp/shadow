package com.github.jengelman.gradle.plugins.shadow.testkit.util

import java.math.BigInteger
import java.util.*

class HashValue(digest: ByteArray) {
  private val digest = BigInteger(1, digest)

  fun asBigInteger(): BigInteger {
    return digest
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is HashValue) {
      return false
    }

    return digest == other.digest
  }

  override fun hashCode(): Int {
    return digest.hashCode()
  }
}
