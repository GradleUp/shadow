package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import org.junit.jupiter.api.Test

class SourceRemapperTest {

  @Test
  fun chainedRelocatorsDoNotCascade() {
    val r1 = SimpleRelocator("a.foo", "b.foo")
    val r2 = SimpleRelocator("b.foo", "c.foo")
    val relocators = listOf(r1, r2)

    val input =
      """
      |package a.foo;
      |import b.foo.Bar;
      |public class Main {
      |  a.foo.Baz baz;
      |  b.foo.Bar bar;
      |}
      """
        .trimMargin()

    val expected =
      """
      |package b.foo;
      |import c.foo.Bar;
      |public class Main {
      |  b.foo.Baz baz;
      |  c.foo.Bar bar;
      |}
      """
        .trimMargin()

    assertThat(relocators.remapSource(input)).isEqualTo(expected)
  }

  @Test
  fun relocateSourcePathWithClassInclude() {
    val relocator =
      SimpleRelocator(
        "pkg",
        "hidden.pkg",
        includes = listOf("pkg.A", "pkg.sub.*"),
      )
    val relocators = listOf(relocator)

    // Included class files
    assertThat(relocators.relocateSourcePath("pkg/A.java")).isEqualTo("hidden/pkg/A.java")
    assertThat(relocators.relocateSourcePath("pkg/A.kt")).isEqualTo("hidden/pkg/A.kt")
    assertThat(relocators.relocateSourcePath("pkg/sub/Nested.java"))
      .isEqualTo("hidden/pkg/sub/Nested.java")

    // Excluded / un-included class file
    assertThat(relocators.relocateSourcePath("pkg/B.java")).isEqualTo("pkg/B.java")
    assertThat(relocators.relocateSourcePath("other/Other.java")).isEqualTo("other/Other.java")
  }

  @Test
  fun relocateSourcePathWithClassExclude() {
    val relocator =
      SimpleRelocator(
        "pkg",
        "hidden.pkg",
        excludes = listOf("pkg.B"),
      )
    val relocators = listOf(relocator)

    assertThat(relocators.relocateSourcePath("pkg/A.java")).isEqualTo("hidden/pkg/A.java")
    assertThat(relocators.relocateSourcePath("pkg/B.java")).isEqualTo("pkg/B.java")
  }

  @Test
  fun remapSourceWithIncludesAndExcludes() {
    val relocator =
      SimpleRelocator(
        "com.example",
        "shaded.example",
        includes = listOf("com.example.used.*"),
        excludes = listOf("com.example.used.Excluded"),
      )
    val relocators = listOf(relocator)

    val input =
      """
      |package com.example.used;
      |import com.example.used.Foo;
      |import com.example.used.Excluded;
      |import com.example.unused.Bar;
      """
        .trimMargin()

    val expected =
      """
      |package shaded.example.used;
      |import shaded.example.used.Foo;
      |import com.example.used.Excluded;
      |import com.example.unused.Bar;
      """
        .trimMargin()

    assertThat(relocators.remapSource(input)).isEqualTo(expected)
  }
}
