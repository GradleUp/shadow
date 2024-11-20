package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.InputStream

data class TransformerContext @JvmOverloads constructor(
  val path: String,
  val inputStream: InputStream,
  val relocators: List<Relocator> = emptyList(),
  val stats: ShadowStats = ShadowStats(),
) {
  class Builder {
    private var path = ""
    private var inputStream: InputStream? = null
    private var relocators = emptyList<Relocator>()
    private var stats = ShadowStats()

    fun path(path: String): Builder = apply { this.path = path }
    fun inputStream(inputStream: InputStream): Builder = apply { this.inputStream = inputStream }
    fun relocators(relocators: List<Relocator>): Builder = apply { this.relocators = relocators }
    fun stats(stats: ShadowStats): Builder = apply { this.stats = stats }
    fun build(): TransformerContext = TransformerContext(
      path = path,
      inputStream = inputStream ?: error("inputStream is required"),
      relocators = relocators,
      stats = stats,
    )
  }

  companion object {
    @JvmStatic
    fun builder(): Builder = Builder()

    @JvmStatic
    fun getEntryTimestamp(preserveFileTimestamps: Boolean, entryTime: Long): Long {
      return if (preserveFileTimestamps) entryTime else ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    }
  }
}
