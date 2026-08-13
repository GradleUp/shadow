package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import java.io.InputStream

@Suppress("DEPRECATION")
public data class TransformerContext
@JvmOverloads
constructor(
  val path: String,
  val inputStream: InputStream,
  val relocators: Set<Relocator> = emptySet(),
) {
  @Deprecated(
    message = "Use `TransformerContext` constructor instead. Will be removed in Shadow 10.",
    replaceWith = ReplaceWith("TransformerContext"),
  )
  public class Builder {
    private var path = ""
    private var inputStream: InputStream? = null
    private var relocators = emptySet<Relocator>()

    public fun path(path: String): Builder = apply { this.path = path }

    public fun inputStream(inputStream: InputStream): Builder = apply {
      this.inputStream = inputStream
    }

    public fun relocators(relocators: Set<Relocator>): Builder = apply {
      this.relocators = relocators
    }

    public fun build(): TransformerContext =
      TransformerContext(
        path = path,
        inputStream = requireNotNull(inputStream) { "inputStream is required" },
        relocators = relocators,
      )
  }

  public companion object {
    @Deprecated(
      message = "Use `TransformerContext` constructor instead. Will be removed in Shadow 10.",
      replaceWith = ReplaceWith("TransformerContext"),
    )
    @JvmStatic
    public fun builder(): Builder = Builder()
  }
}
