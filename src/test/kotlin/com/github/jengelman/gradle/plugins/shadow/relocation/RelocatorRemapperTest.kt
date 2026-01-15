package com.github.jengelman.gradle.plugins.shadow.relocation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.RelocatorRemapper
import java.lang.constant.ClassDesc
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class RelocatorRemapperTest {
  @ParameterizedTest
  @MethodSource("signaturePatternsProvider")
  fun relocateSignaturePatterns(input: String, expected: String) {
    val relocator =
      RelocatorRemapper(relocators = setOf(SimpleRelocator("org.package", "shadow.org.package")))
    assertThat(relocator.map(ClassDesc.of(input))).isEqualTo(expected)
  }

  private companion object {
    val primitiveTypes = setOf('B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z')

    val primitiveTypePatterns =
      primitiveTypes.map {
        // Methods like `void method(boolean arg1, org.package.ClassA arg2)`
        Arguments.of("(${it}Lorg/package/ClassA;)V", "(${it}Lshadow/org/package/ClassA;)V")
      }

    @JvmStatic
    fun signaturePatternsProvider() =
      listOf(
        // Normal class: `org.package.ClassA`
        Arguments.of("Lorg/package/ClassA;", "Lshadow/org/package/ClassA;"),
        // Array class: `org.package.ClassA[]`
        Arguments.of("[Lorg/package/ClassA;", "[Lshadow/org/package/ClassA;"),
        // Multidimensional array of class: `org.package.ClassA[][]`
        Arguments.of("[[Lorg/package/ClassA;", "[[Lshadow/org/package/ClassA;"),
        // Multiple classes: `org.package.ClassA org.package.ClassB`
        Arguments.of(
          "Lorg/package/ClassA;Lorg/package/ClassB;",
          "Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;",
        ),
        // Multiple classes: `java.lang.Object org.package.ClassB`
        Arguments.of(
          "Ljava/lang/Object;Lorg/package/ClassB;",
          "Ljava/lang/Object;Lshadow/org/package/ClassB;",
        ),
        // Single method argument: `void method(org.package.ClassA arg);`
        Arguments.of("(Lorg/package/ClassA;)", "(Lshadow/org/package/ClassA;)"),
        // Method arguments: `void method(org.package.ClassA arg1, org.package.ClassB arg2);`
        Arguments.of(
          "(Lorg/package/ClassA;Lorg/package/ClassB;)",
          "(Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;)",
        ),
        // Example from issue 1403.
        Arguments.of(
          "()Lorg/package/ClassA;Lorg/package/ClassB;",
          "()Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;",
        ),
      ) + primitiveTypePatterns
  }
}
