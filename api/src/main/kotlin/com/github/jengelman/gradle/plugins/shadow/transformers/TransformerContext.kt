package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import java.io.InputStream
import java.util.GregorianCalendar

public data class TransformerContext @JvmOverloads constructor(
  val path: String,
  val inputStream: InputStream? = null,
  val relocators: List<Relocator> = emptyList(),
  val stats: ShadowStats = ShadowStats(),
) {
  public class Builder {
    private var path = ""
    private var inputStream: InputStream? = null
    private var relocators = emptyList<Relocator>()
    private var stats = ShadowStats()

    public fun path(path: String): Builder = apply { this.path = path }

    public fun inputStream(inputStream: InputStream): Builder = apply { this.inputStream = inputStream }

    public fun relocators(relocators: List<Relocator>): Builder = apply { this.relocators = relocators }

    public fun stats(stats: ShadowStats): Builder = apply { this.stats = stats }

    public fun build(): TransformerContext {
      return TransformerContext(
        path = path,
        inputStream = inputStream,
        relocators = relocators,
        stats = stats,
      )
    }
  }

  public companion object {
    // TODO: replace it with ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    private val CONSTANT_TIME_FOR_ZIP_ENTRIES: Long = GregorianCalendar(1980, 1, 1, 0, 0, 0).getTimeInMillis()

    @JvmStatic
    public fun builder(): Builder = Builder()

    @JvmStatic
    public fun getEntryTimestamp(preserveFileTimestamps: Boolean, entryTime: Long): Long {
      return if (preserveFileTimestamps) entryTime else CONSTANT_TIME_FOR_ZIP_ENTRIES
    }
  }
}
