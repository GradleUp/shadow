package com.github.jengelman.gradle.plugins.shadow.util

import java.math.BigInteger

data class HashValue(val digest: BigInteger) {
  constructor(digest: ByteArray) : this(BigInteger(1, digest))
}
