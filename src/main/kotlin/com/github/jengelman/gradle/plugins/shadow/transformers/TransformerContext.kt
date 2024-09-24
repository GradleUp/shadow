package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.InputStream

public data class TransformerContext(
  val path: String,
  val inputStream: InputStream,
  val relocators: List<Relocator>,
  val stats: ShadowStats,
) {
  public class Builder {
    private var path: String? = null
    private var inputStream: InputStream? = null
    private var relocators: List<Relocator>? = null
    private var stats: ShadowStats? = null

    public fun path(path: String): Builder = apply { this.path = path }
    public fun inputStream(inputStream: InputStream): Builder = apply { this.inputStream = inputStream }
    public fun relocators(relocators: List<Relocator>): Builder = apply { this.relocators = relocators }
    public fun stats(stats: ShadowStats): Builder = apply { this.stats = stats }

    public fun build(): TransformerContext {
      return TransformerContext(
        requireNotNull(path),
        requireNotNull(inputStream),
        requireNotNull(relocators),
        requireNotNull(stats),
      )
    }
  }

  public companion object {
    @JvmStatic
    public fun getEntryTimestamp(preserveFileTimestamps: Boolean, entryTime: Long): Long {
      return if (preserveFileTimestamps) entryTime else ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    }

    @JvmStatic
    public fun builder(): Builder = Builder()
  }
}
