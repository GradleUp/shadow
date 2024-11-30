package com.github.jengelman.gradle.plugins.shadow.unit.transformers

import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFileTreeElement
import org.gradle.testfixtures.ProjectBuilder

abstract class TransformerTestSupport<T : Transformer> {
  protected lateinit var transformer: T

  protected companion object {
    val objectFactory = ProjectBuilder.builder().build().objects

    fun getFileElement(path: String): FileTreeElement {
      return DefaultFileTreeElement(null, RelativePath.parse(true, path), null, null)
    }
  }
}
