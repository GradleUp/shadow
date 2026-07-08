package com.github.jengelman.gradle.plugins.shadow.relocation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.mapName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class RelocatorsTest {
  @ParameterizedTest
  @MethodSource("signaturePatternsProvider")
  fun relocateSignaturePatterns(input: String, expected: String) {
    val actual = setOf(SimpleRelocator("org.package", "shadow.org.package")).mapName(name = input)
    assertThat(actual).isEqualTo(expected)
  }

  /**
   * Verifies that a relocator with [Relocator.skipStringConstants] = true is skipped individually
   * when mapping literals, rather than short-circuiting the entire set of relocators.
   */
  @Test
  fun skipStringConstantsIsPerRelocator() {
    val skippingRelocator =
      SimpleRelocator("org.package", "shadow.org.package", skipStringConstants = true)
    val normalRelocator = SimpleRelocator("com.example", "shadow.com.example")
    val relocators = linkedSetOf(skippingRelocator, normalRelocator)

    // The skipping relocator cannot match "com.example.Foo", but the normal one can.
    // Ensure the normal relocator is still applied even though the first one has
    // skipStringConstants.
    assertThat(relocators.mapName("com.example.Foo", mapLiterals = true))
      .isEqualTo("shadow.com.example.Foo")
    // When mapLiterals=true, the skipping relocator should not apply to its own pattern.
    assertThat(relocators.mapName("org.package.Bar", mapLiterals = true))
      .isEqualTo("org.package.Bar")
    // When mapLiterals=false, the skipping relocator applies normally.
    assertThat(relocators.mapName("org.package.Bar", mapLiterals = false))
      .isEqualTo("shadow.org.package.Bar")
  }

  private companion object {
    val primitiveTypes = setOf('B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z')

    val primitiveTypePatterns = primitiveTypes.map {
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
        // Class name in slashy format (used in capturing class, impl class, and functional
        // interface class of SerializedLambda)
        Arguments.of(
          "org/package/ClassA",
          "shadow/org/package/ClassA",
        ),
        // Method descriptor formats found in lambda deserialization metadata
        Arguments.of(
          "(Lorg/package/ClassA;)Ljava/lang/String;",
          "(Lshadow/org/package/ClassA;)Ljava/lang/String;",
        ),
        Arguments.of(
          "(Lorg/package/ClassA;I)V",
          "(Lshadow/org/package/ClassA;I)V",
        ),
        Arguments.of(
          "(ILorg/package/ClassA;)V",
          "(ILshadow/org/package/ClassA;)V",
        ),
        Arguments.of(
          "(Lorg/package/ClassA;Lorg/package/ClassB;)Lorg/package/ClassA;",
          "(Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;)Lshadow/org/package/ClassA;",
        ),
      ) + primitiveTypePatterns
  }
}
