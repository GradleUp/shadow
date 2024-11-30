package com.github.jengelman.gradle.plugins.shadow.unit

import assertk.fail
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.relocation.SimpleRelocatorParameterTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/relocation/SimpleRelocatorParameterTest.java).
 *
 * @author John Engelman
 */
class SimpleRelocatorParameterTest {
  @Test
  fun testThatNullPatternInConstructorShouldNotThrowNullPointerException() {
    constructThenFailOnNullPointerException(null, "")
  }

  @Test
  fun testThatNullShadedPatternInConstructorShouldNotThrowNullPointerException() {
    constructThenFailOnNullPointerException("", null)
  }

  private fun constructThenFailOnNullPointerException(pattern: String?, shadedPattern: String?) {
    try {
      SimpleRelocator(pattern, shadedPattern)
    } catch (e: NullPointerException) {
      fail("Constructor should not throw null pointer exceptions")
    }
  }
}
