package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import org.junit.jupiter.api.Test

class SourceRemapperTest {

  @Test
  fun appliesCustomRelocatorAlongsideSimpleRelocator() {
    val customRelocator =
      object : Relocator {
        override fun canRelocatePath(path: String) = false

        override fun relocatePath(context: RelocatePathContext) = context.path

        override fun canRelocateClass(className: String) = false

        override fun relocateClass(context: RelocateClassContext) = context.className

        override fun applyToSourceContent(sourceContent: String) =
          sourceContent.replace("CUSTOM_NAME", "RELOCATED_NAME")
      }
    val relocators = listOf(SimpleRelocator("com.example", "shaded.example"), customRelocator)
    val input =
      """
      |package com.example;
      |class Main {
      |  String value = CUSTOM_NAME;
      |}
      """
        .trimMargin()

    val expected =
      """
      |package shaded.example;
      |class Main {
      |  String value = RELOCATED_NAME;
      |}
      """
        .trimMargin()

    assertThat(relocators.remapSource(input)).isEqualTo(expected)
  }

  @Test
  fun relocatesQualifiedNamesInExpressionsAndTypeAnnotations() {
    val relocators = listOf(SimpleRelocator("com.example", "shaded.example"))
    val input =
      """
      |class Main {
      |  void method() {
      |    return com.example.Factory.create();
      |  }
      |  com.example.Type value = new com.example.Type();
      |  val typed: com.example.Type = com.example.Factory.create()
      |}
      """
        .trimMargin()

    val expected =
      """
      |class Main {
      |  void method() {
      |    return shaded.example.Factory.create();
      |  }
      |  shaded.example.Type value = new shaded.example.Type();
      |  val typed: shaded.example.Type = shaded.example.Factory.create()
      |}
      """
        .trimMargin()

    assertThat(relocators.remapSource(input)).isEqualTo(expected)
  }

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
