package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.InputStream

data class TransformerContext(
  val path: String,
  val inputStream: InputStream,
  val relocators: List<Relocator>,
  val stats: ShadowStats,
) {
  class Builder {
    private var path: String? = null
    private var inputStream: InputStream? = null
    private var relocators: List<Relocator>? = null
    private var stats: ShadowStats? = null

    fun path(path: String) = apply { this.path = path }
    fun inputStream(inputStream: InputStream) = apply { this.inputStream = inputStream }
    fun relocators(relocators: List<Relocator>) = apply { this.relocators = relocators }
    fun stats(stats: ShadowStats) = apply { this.stats = stats }

    fun build(): TransformerContext {
      return TransformerContext(
        requireNotNull(path),
        requireNotNull(inputStream),
        requireNotNull(relocators),
        requireNotNull(stats),
      )
    }
  }

  companion object {
    fun getEntryTimestamp(preserveFileTimestamps: Boolean, entryTime: Long): Long {
      return if (preserveFileTimestamps) entryTime else ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    }

    fun builder() = Builder()
  }
}
