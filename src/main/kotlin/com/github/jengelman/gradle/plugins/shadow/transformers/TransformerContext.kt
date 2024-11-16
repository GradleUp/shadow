package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.InputStream

public data class TransformerContext @JvmOverloads constructor(
  val path: String,
  val inputStream: InputStream? = null,
  val relocators: List<Relocator> = emptyList(),
  val stats: ShadowStats = ShadowStats(),
) {
  public open class Builder {
    private var path = ""
    private var inputStream: InputStream? = null
    private var relocators = emptyList<Relocator>()
    private var stats = ShadowStats()

    public open fun path(path: String): Builder = apply { this.path = path }

    public open fun inputStream(inputStream: InputStream): Builder = apply { this.inputStream = inputStream }

    public open fun relocators(relocators: List<Relocator>): Builder = apply { this.relocators = relocators }

    public open fun stats(stats: ShadowStats): Builder = apply { this.stats = stats }

    public open fun build(): TransformerContext {
      return TransformerContext(
        path = path,
        inputStream = inputStream,
        relocators = relocators,
        stats = stats,
      )
    }
  }

  public companion object {
    @JvmStatic
    public open fun builder(): Builder = Builder()

    @JvmStatic
    public open fun getEntryTimestamp(preserveFileTimestamps: Boolean, entryTime: Long): Long {
      return if (preserveFileTimestamps) entryTime else ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    }
  }
}
