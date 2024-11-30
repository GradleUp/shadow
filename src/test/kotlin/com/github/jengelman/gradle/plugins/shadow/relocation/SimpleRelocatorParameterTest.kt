package com.github.jengelman.gradle.plugins.shadow.relocation

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.fail

/**
 * Modified from org.apache.maven.plugins.shade.relocation.SimpleRelocatorParameterTest.java
 */
class SimpleRelocatorParameterTest {

  @Test
  void testThatNullPatternInConstructorShouldNotThrowNullPointerException() {
    constructThenFailOnNullPointerException(null, "")
  }

  @Test
  void testThatNullShadedPatternInConstructorShouldNotThrowNullPointerException() {
    constructThenFailOnNullPointerException("", null)
  }

  private static void constructThenFailOnNullPointerException(String pattern, String shadedPattern) {
    try {
      new SimpleRelocator(pattern, shadedPattern)
    }
    catch (NullPointerException ignored) {
      fail("Constructor should not throw null pointer exceptions")
    }
  }
}
