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

  @Test
  fun relocateSourcePathWithClassOnlyRelocator() {
    val classOnlyRelocator =
      object : Relocator {
        override fun canRelocatePath(path: String) = false

        override fun relocatePath(context: RelocatePathContext) = context.path

        override fun canRelocateClass(className: String) = className.startsWith("custom.pkg.")

        override fun relocateClass(context: RelocateClassContext) =
          context.className.replaceFirst("custom.pkg.", "shaded.pkg.")

        override fun applyToSourceContent(sourceContent: String) = sourceContent
      }
    val relocators = listOf(classOnlyRelocator)

    assertThat(relocators.relocateSourcePath("custom/pkg/MyClass.java"))
      .isEqualTo("shaded/pkg/MyClass.java")
    assertThat(relocators.relocateSourcePath("custom/pkg/sub/OtherClass.kt"))
      .isEqualTo("shaded/pkg/sub/OtherClass.kt")
    assertThat(relocators.relocateSourcePath("unrelated/pkg/Unrelated.java"))
      .isEqualTo("unrelated/pkg/Unrelated.java")
  }

  @Test
  fun remapSourceWithMultiLanguageConstructs() {
    val relocators = listOf(SimpleRelocator("com.example", "shaded.example"))
    val input =
      """
      |// Kotlin constructs
      |val delegate by com.example.Delegate()
      |val isType = obj is com.example.Type
      |val asType = obj as com.example.Type
      |val range = 0..com.example.Constants.MAX
      |fun compute(factory: () -> com.example.Type = { com.example.Factory.create() })
      |
      |// Groovy constructs
      |def dynamicVar = com.example.Factory.create()
      |def coerced = obj as com.example.Type
      |
      |// Scala constructs
      |case _: com.example.Type => true
      |class MyService with com.example.Trait
      |
      |// Java expressions and operators
      |boolean flag = condition ? com.example.Factory.create() : null;
      |int divided = total / com.example.Constants.SCALE;
      |int bitwise = flags & com.example.Constants.MASK;
      """
        .trimMargin()

    val expected =
      """
      |// Kotlin constructs
      |val delegate by shaded.example.Delegate()
      |val isType = obj is shaded.example.Type
      |val asType = obj as shaded.example.Type
      |val range = 0..shaded.example.Constants.MAX
      |fun compute(factory: () -> shaded.example.Type = { shaded.example.Factory.create() })
      |
      |// Groovy constructs
      |def dynamicVar = shaded.example.Factory.create()
      |def coerced = obj as shaded.example.Type
      |
      |// Scala constructs
      |case _: shaded.example.Type => true
      |class MyService with shaded.example.Trait
      |
      |// Java expressions and operators
      |boolean flag = condition ? shaded.example.Factory.create() : null;
      |int divided = total / shaded.example.Constants.SCALE;
      |int bitwise = flags & shaded.example.Constants.MASK;
      """
        .trimMargin()

    assertThat(relocators.remapSource(input)).isEqualTo(expected)
  }

  @Test
  fun remapSourceProtectsPrefixCollisionsAndVariables() {
    val relocators =
      listOf(
        SimpleRelocator("com.example", "shaded.example"),
        SimpleRelocator("io", "shaded.io"),
      )
    val input =
      """
      |package io;
      |import io.netty.channel.Channel;
      |import other.com.example.Foo;
      |import java.io.IOException;
      |
      |class Main {
      |  String io, val;
      |  String path = "dir/com/example/File.txt";
      |  String relocatedPath = "com/example/File.txt";
      |  /* comment */ com.example.Type t;
      |  // line comment com.example.Type t2;
      |}
      """
        .trimMargin()

    val expected =
      """
      |package shaded.io;
      |import shaded.io.netty.channel.Channel;
      |import other.com.example.Foo;
      |import java.io.IOException;
      |
      |class Main {
      |  String io, val;
      |  String path = "dir/com/example/File.txt";
      |  String relocatedPath = "shaded/example/File.txt";
      |  /* comment */ shaded.example.Type t;
      |  // line comment shaded.example.Type t2;
      |}
      """
        .trimMargin()

    assertThat(relocators.remapSource(input)).isEqualTo(expected)
  }

  @Test
  fun remapSourceAtContentStartAndInJavadocLink() {
    val relocators =
      listOf(
        SimpleRelocator("com.example", "shaded.example"),
        SimpleRelocator("io", "shaded.io"),
      )
    val input =
      """
      |com.example.Factory.create()
      |io.netty.channel.Channel.open()
      |/**
      | * See {@link io} or {@link com.example.Type}
      | */
      """
        .trimMargin()

    val expected =
      """
      |shaded.example.Factory.create()
      |shaded.io.netty.channel.Channel.open()
      |/**
      | * See {@link shaded.io} or {@link shaded.example.Type}
      | */
      """
        .trimMargin()

    assertThat(relocators.remapSource(input)).isEqualTo(expected)
  }
}
