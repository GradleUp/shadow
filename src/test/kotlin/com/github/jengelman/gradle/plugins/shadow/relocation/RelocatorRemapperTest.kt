package com.github.jengelman.gradle.plugins.shadow.relocation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.RelocatorRemapper
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class RelocatorRemapperTest {
  @ParameterizedTest
  @MethodSource("classSignatureStringConstants")
  fun relocateSignaturePatterns(input: String, expected: String) {
    val relocator = RelocatorRemapper(
      relocators = setOf(
        SimpleRelocator("org.package", "shadow.org.package"),
      ),
    )
    assertThat(relocator.map(input)).isEqualTo(expected)
  }

  private companion object {
    @JvmStatic
    fun classSignatureStringConstants() = listOf(
      Arguments.of("Lorg/package/ClassA;", "Lshadow/org/package/ClassA;"),
      Arguments.of("Lorg/package/ClassA;Lorg/package/ClassB;", "Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;"),
      Arguments.of("Ljava/lang/Object;Lorg/package/ClassB;", "Ljava/lang/Object;Lshadow/org/package/ClassB;"),
      Arguments.of("(Lorg/package/ClassA;Lorg/package/ClassB;)", "(Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;)"),
      Arguments.of("()Lorg/package/ClassA;Lorg/package/ClassB;", "()Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;"),
    )
  }
}
