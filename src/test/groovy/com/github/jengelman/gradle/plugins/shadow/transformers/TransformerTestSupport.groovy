package com.github.jengelman.gradle.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFileTreeElement
import org.gradle.testfixtures.ProjectBuilder

abstract class TransformerTestSupport<T extends Transformer> {
  protected static T transformer
  protected static final def objectFactory = ProjectBuilder.builder().build().objects
  protected static final String MANIFEST_NAME = "META-INF/MANIFEST.MF"

  protected static FileTreeElement getFileElement(String path) {
    return new DefaultFileTreeElement(null, RelativePath.parse(true, path), null, null)
  }

  /**
   * NOTE: The Turkish locale has an usual case transformation for the letters "I" and "i", making it a prime
   * choice to test for improper case-less string comparisons.
   */
  protected static setupTurkishLocale() {
    Locale.setDefault(new Locale("tr"))
  }

  protected InputStream requireResourceAsStream(String resource) {
    this.class.classLoader.getResourceAsStream(resource)
  }
}
