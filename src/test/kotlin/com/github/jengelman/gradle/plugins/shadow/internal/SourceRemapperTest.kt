package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class SourceRemapperTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("remapSourceProvider")
  fun remapSource(
    name: String,
    relocators: List<Relocator>,
    input: String,
    expected: String,
  ) {
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
  fun relocateSourcePathWithClassOnlyRelocator() {
    val classOnlyRelocator =
      object : Relocator by DefaultRelocator {
        override fun canRelocateClass(className: String) = className.startsWith("custom.pkg.")

        override fun relocateClass(context: RelocateClassContext) =
          context.className.replaceFirst("custom.pkg.", "shaded.pkg.")
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
  fun relocateSourcePathWithNonSourceFileDelegatesToRelocatePath() {
    val relocators = listOf(SimpleRelocator("pkg", "hidden.pkg"))

    // Resource files and class files are handled via relocatePath
    assertThat(relocators.relocateSourcePath("pkg/config.properties"))
      .isEqualTo("hidden/pkg/config.properties")
    assertThat(relocators.relocateSourcePath("pkg/A.class")).isEqualTo("hidden/pkg/A.class")
    assertThat(relocators.relocateSourcePath("other/file.txt")).isEqualTo("other/file.txt")
  }

  @Test
  fun relocateSourcePathWithMultipleRelocators() {
    val r1 = SimpleRelocator("pkg.one", "shaded.one")
    val r2 = SimpleRelocator("pkg.two", "shaded.two")
    val relocators = listOf(r1, r2)

    assertThat(relocators.relocateSourcePath("pkg/one/Foo.groovy"))
      .isEqualTo("shaded/one/Foo.groovy")
    assertThat(relocators.relocateSourcePath("pkg/two/Bar.scala")).isEqualTo("shaded/two/Bar.scala")
    assertThat(relocators.relocateSourcePath("pkg/three/Baz.java")).isEqualTo("pkg/three/Baz.java")
  }

  @Test
  fun relocateSourcePathWithEmptyRelocators() {
    assertThat(emptyList<Relocator>().relocateSourcePath("com/example/Foo.java"))
      .isEqualTo("com/example/Foo.java")
  }

  private companion object {
    @JvmStatic
    fun remapSourceProvider(): List<Arguments> =
      listOf(
        Arguments.of(
          "custom relocator alongside SimpleRelocator",
          listOf(
            SimpleRelocator("com.example", "shaded.example"),
            object : Relocator by DefaultRelocator {
              override fun applyToSourceContent(sourceContent: String) =
                sourceContent.replace("CUSTOM_NAME", "RELOCATED_NAME")
            },
          ),
          """
          |package com.example;
          |class Main {
          |  String value = CUSTOM_NAME;
          |}
          """
            .trimMargin(),
          """
          |package shaded.example;
          |class Main {
          |  String value = RELOCATED_NAME;
          |}
          """
            .trimMargin(),
        ),
        Arguments.of(
          "multiple relocators sequentially",
          listOf(
            SimpleRelocator("a.foo", "b.foo"),
            SimpleRelocator("b.foo", "c.foo"),
          ),
          """
          |package a.foo;
          |import b.foo.Bar;
          |public class Main {
          |  a.foo.Baz baz;
          |  b.foo.Bar bar;
          |}
          """
            .trimMargin(),
          """
          |package c.foo;
          |import c.foo.Bar;
          |public class Main {
          |  c.foo.Baz baz;
          |  c.foo.Bar bar;
          |}
          """
            .trimMargin(),
        ),
        Arguments.of(
          "prefix collisions and variables protection",
          listOf(
            SimpleRelocator("com.example", "shaded.example"),
            SimpleRelocator("io", "shaded.io"),
          ),
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
            .trimMargin(),
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
            .trimMargin(),
        ),
        Arguments.of(
          "content start offset 0 and single-word Javadoc link",
          listOf(
            SimpleRelocator("com.example", "shaded.example"),
            SimpleRelocator("io", "shaded.io"),
          ),
          """
          |com.example.Factory.create()
          |io.netty.channel.Channel.open()
          |/**
          | * See {@link io} or {@link com.example.Type}
          | */
          """
            .trimMargin(),
          """
          |shaded.example.Factory.create()
          |shaded.io.netty.channel.Channel.open()
          |/**
          | * See {@link shaded.io} or {@link shaded.example.Type}
          | */
          """
            .trimMargin(),
        ),
        Arguments.of(
          "empty relocators",
          emptyList<Relocator>(),
          """
          |package com.example;
          |class Foo {}
          """
            .trimMargin(),
          """
          |package com.example;
          |class Foo {}
          """
            .trimMargin(),
        ),
      )
  }
}

private object DefaultRelocator : Relocator {
  override fun canRelocatePath(path: String) = false

  override fun relocatePath(context: RelocatePathContext) = context.path

  override fun canRelocateClass(className: String) = false

  override fun relocateClass(context: RelocateClassContext) = context.className

  override fun applyToSourceContent(sourceContent: String): String = sourceContent
}
