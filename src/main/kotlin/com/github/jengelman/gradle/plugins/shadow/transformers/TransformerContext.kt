package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.InputStream

public data class TransformerContext @JvmOverloads constructor(
  val path: String,
  val inputStream: InputStream,
  val relocators: Set<Relocator> = emptySet(),
  val stats: ShadowStats = ShadowStats(),
) {
  public class Builder {
    private var path = ""
    private var inputStream: InputStream? = null
    private var relocators = emptySet<Relocator>()
    private var stats = ShadowStats()

    public fun path(path: String): Builder = apply { this.path = path }
    public fun inputStream(inputStream: InputStream): Builder = apply { this.inputStream = inputStream }
    public fun relocators(relocators: Set<Relocator>): Builder = apply { this.relocators = relocators }
    public fun stats(stats: ShadowStats): Builder = apply { this.stats = stats }
    public fun build(): TransformerContext = TransformerContext(
      path = path,
      inputStream = inputStream ?: error("inputStream is required"),
      relocators = relocators,
      stats = stats,
    )
  }

  public companion object {
    @JvmStatic
    public fun builder(): Builder = Builder()

    @JvmStatic
    public fun getEntryTimestamp(preserveFileTimestamps: Boolean, entryTime: Long): Long {
      return if (preserveFileTimestamps) entryTime else ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    }
  }
}
