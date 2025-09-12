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
    val primitiveTypes = setOf('B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z')

    val primitiveTypeArguments = primitiveTypes.map {
      // Methods like `void method(boolean arg1, org.package.ClassA arg2)`
      Arguments.of("(${it}Lorg/package/ClassA;)V", "(${it}Lshadow/org/package/ClassA;)V")
    }

    @JvmStatic
    fun classSignatureStringConstants() = listOf(
      // Normal class.
      Arguments.of("Lorg/package/ClassA;", "Lshadow/org/package/ClassA;"),
      // Array class.
      Arguments.of("[Lorg/package/ClassA;", "[Lshadow/org/package/ClassA;"),
      // Multidimensional array of class.
      Arguments.of("[[Lorg/package/ClassA;", "[[Lshadow/org/package/ClassA;"),
      // Multiple classes.
      Arguments.of("Lorg/package/ClassA;Lorg/package/ClassB;", "Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;"),
      // Multiple classes.
      Arguments.of("Ljava/lang/Object;Lorg/package/ClassB;", "Ljava/lang/Object;Lshadow/org/package/ClassB;"),
      // Single method argument.
      Arguments.of("(Lorg/package/ClassA;)", "(Lshadow/org/package/ClassA;)"),
      // Method arguments.
      Arguments.of("(Lorg/package/ClassA;Lorg/package/ClassB;)", "(Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;)"),
      // Method return types.
      Arguments.of("()Lorg/package/ClassA;Lorg/package/ClassB;", "()Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;"),
    ) + primitiveTypeArguments
  }
}
