package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import org.junit.jupiter.api.Test

/**
 * Modified from
 * [org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ApacheNoticeResourceTransformerTest.java).
 */
class ApacheNoticeResourceTransformerTest : BaseTransformerTest<ApacheNoticeResourceTransformer>() {

  init {
    setupTurkishLocale()
  }

  @Test
  fun canTransformResource() =
    with(transformer) {
      assertThat(canTransformResource("META-INF/NOTICE")).isTrue()
      assertThat(canTransformResource("META-INF/NOTICE.TXT")).isTrue()
      assertThat(canTransformResource("META-INF/Notice.txt")).isTrue()
      assertThat(canTransformResource("META-INF/NOTICE.md")).isTrue()
      assertThat(canTransformResource("META-INF/Notice.md")).isTrue()
      assertThat(canTransformResource("META-INF/MANIFEST.MF")).isFalse()
    }

  @Test
  fun canTransformByPattern() =
    with(transformer) {
      exclude("META-INF/NOTICE.txt")
      include("META-INF/NOTICE.*")
      assertThat(canTransformResource("META-INF/NOTICE.txt")).isFalse()
      assertThat(canTransformResource("META-INF/NOTICE.log")).isTrue()
    }

  @Test
  fun preamble1ShouldHaveATrailingSpace() =
    with(transformer) {
      projectName.set("test-project")
      copyright.set("test-project\nCopyright 2006 The Apache Software Foundation\n")
      transform(textContext(NOTICE_RESOURCE))

      val content = transformToJar().use { it.getContent(NOTICE_RESOURCE) }

      assertThat(content)
        .isEqualTo(
          """
          |// ------------------------------------------------------------------
          |// NOTICE file corresponding to the section 4d of The Apache License,
          |// Version 2.0, in this case for test-project
          |// ------------------------------------------------------------------
          |
          |test-project
          |Copyright 2006 The Apache Software Foundation
          |
          |This product includes software developed at
          |The Apache Software Foundation (https://www.apache.org/).
          """
            .trimMargin()
        )
    }

  @Test
  fun overrideOutputPath() =
    with(transformer) {
      val customNoticeEntry = "META-INF/CUSTOM_NOTICE"
      addHeader.set(false)
      copyright.set("Copyright 2006 The Apache Software Foundation\n")
      outputPath.set(customNoticeEntry)
      transform(textContext(NOTICE_RESOURCE, "Notice from A"))
      transform(textContext(NOTICE_RESOURCE, "Notice from B"))

      val content = transformToJar().use { it.getContent(customNoticeEntry) }

      assertThat(content)
        .isEqualTo(
          """
          |Copyright 2006 The Apache Software Foundation
          |
          |This product includes software developed at
          |The Apache Software Foundation (https://www.apache.org/).
          |
          |Notice from A
          |
          |Notice from B
          """
            .trimMargin()
        )
    }

  private companion object {
    const val NOTICE_RESOURCE = "META-INF/NOTICE"
  }
}
