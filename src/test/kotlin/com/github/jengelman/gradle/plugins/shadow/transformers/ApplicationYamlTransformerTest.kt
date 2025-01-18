package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class ApplicationYamlTransformerTest : BaseTransformerTest<ApplicationYamlTransformer>() {
  @Test
  fun canTransformResource() {
    assertThat(transformer.canTransformResource("resources/application.yml")).isTrue()
    assertThat(transformer.canTransformResource("resources/application.YML")).isTrue()
    assertThat(transformer.canTransformResource("resources/custom-config/application.yml")).isTrue()
    assertThat(transformer.canTransformResource("resources/config/yaml/application.yml")).isTrue()
    assertThat(transformer.canTransformResource("resources/application.yaml")).isFalse()
    assertThat(transformer.canTransformResource("resources/application.YAML")).isFalse()
  }
}
