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
      // Method arguments.
      Arguments.of("(Lorg/package/ClassA;Lorg/package/ClassB;)", "(Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;)"),
      // Method return types.
      Arguments.of("()Lorg/package/ClassA;Lorg/package/ClassB;", "()Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;"),
      // void method(byte arg1, org.package.ClassA arg2)
      Arguments.of("(BLorg/package/ClassA;)V", "(BLshadow/org/package/ClassA;)V"),
      // void method(char arg1, org.package.ClassA arg2)
      Arguments.of("(CLorg/package/ClassA;)V", "(CLshadow/org/package/ClassA;)V"),
      // void method(double arg1, org.package.ClassA arg2)
      Arguments.of("(DLorg/package/ClassA;)V", "(DLshadow/org/package/ClassA;)V"),
      // void method(float arg1, org.package.ClassA arg2)
      Arguments.of("(FLorg/package/ClassA;)V", "(FLshadow/org/package/ClassA;)V"),
      // void method(int arg1, org.package.ClassA arg2)
      Arguments.of("(ILorg/package/ClassA;)V", "(ILshadow/org/package/ClassA;)V"),
      // void method(long arg1, org.package.ClassA arg2)
      Arguments.of("(JLorg/package/ClassA;)V", "(JLshadow/org/package/ClassA;)V"),
      // void method(short arg1, org.package.ClassA arg2)
      Arguments.of("(SLorg/package/ClassA;)V", "(SLshadow/org/package/ClassA;)V"),
      // void method(boolean arg1, org.package.ClassA arg2)
      Arguments.of("(ZLorg/package/ClassA;)V", "(ZLshadow/org/package/ClassA;)V"),
    )
  }
}
