package com.github.jengelman.gradle.plugins.shadow.relocation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Modified from
 * [org.apache.maven.plugins.shade.relocation.SimpleRelocatorTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/relocation/SimpleRelocatorTest.java).
 */
class SimpleRelocatorTest {

  @Test
  fun canRelocatePath() {
    var relocator = SimpleRelocator("org.foo")
    assertThat(relocator.canRelocatePath("org/foo/Class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Class.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/bar/Class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/bar/Class.class")).isTrue()
    assertThat(relocator.canRelocatePath("com/foo/bar/Class")).isFalse()
    assertThat(relocator.canRelocatePath("com/foo/bar/Class.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/Foo/Class")).isFalse()
    assertThat(relocator.canRelocatePath("org/Foo/Class.class")).isFalse()

    // Verify paths starting with '/'
    assertThat(relocator.canRelocatePath("/org/Foo/Class")).isFalse()
    assertThat(relocator.canRelocatePath("/org/Foo/Class.class")).isFalse()

    relocator =
      SimpleRelocator(
        "org.foo",
        excludes =
          listOf(
            "org.foo.Excluded",
            "org.foo.public.*",
            "org.foo.recurse.**",
            "org.foo.Public*Stuff",
          ),
      )
    assertThat(relocator.canRelocatePath("org/foo/Class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Class.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/excluded")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Excluded")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/Excluded.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/public")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/public/Class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/public/Class.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/public/sub")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/public/sub/Class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/publicRELOC/Class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/PrivateStuff")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/PrivateStuff.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/PublicStuff")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/PublicStuff.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/PublicUtilStuff")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/PublicUtilStuff.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/recurse")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/recurse/Class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/recurse/Class.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/recurse/sub")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/recurse/sub/Class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/recurse/sub/Class.class")).isFalse()

    // Verify edge cases
    relocator = SimpleRelocator("org.f")
    assertThat(relocator.canRelocatePath("")).isFalse() // Empty path
    assertThat(relocator.canRelocatePath(".class")).isFalse() // only .class
    assertThat(relocator.canRelocatePath("te")).isFalse() // shorter than path pattern
    assertThat(relocator.canRelocatePath("test")).isFalse() // shorter than path pattern with /
    assertThat(relocator.canRelocatePath("org/f")).isTrue() // equal to path pattern
    assertThat(relocator.canRelocatePath("/org/f")).isTrue() // equal to path pattern with /

    relocator = SimpleRelocator("foo.")
    assertThat(relocator.canRelocatePath("foo/foo.bar")).isTrue()
    relocator.exclude("foo/foo.bar")
    assertThat(relocator.canRelocatePath("foo/foo.bar"))
      .isFalse() // Don't handle file path pattern.
    assertThat(relocator.canRelocatePath("foo/foobar")).isTrue()
    relocator.exclude("foo/foobar")
    assertThat(relocator.canRelocatePath("foo/foobar")).isFalse() // File without extension.
  }

  @Test
  fun canRelocatePathWithRegex() {
    // Include with Regex
    var relocator = SimpleRelocator("org.foo", includes = listOf("%regex[org/foo/R(\\$.*)?$]"))
    assertThat(relocator.canRelocatePath("org/foo/R.class")).isTrue()
    assertThat(relocator.canRelocatePath($$"org/foo/R$string.class")).isTrue()
    assertThat(relocator.canRelocatePath($$"org/foo/R$layout.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Recording/R.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/Recording.class")).isFalse()
    assertThat(relocator.canRelocatePath($$"org/foo/bar/R$string.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/R.class")).isFalse()
    assertThat(relocator.canRelocatePath($$"org/R$string.class")).isFalse()

    // Exclude with Regex
    relocator = SimpleRelocator("org.foo", excludes = listOf("%regex[org/foo/.*Factory[0-9].*]"))
    assertThat(relocator.canRelocatePath("org/foo/Factory.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/FooFactoryMain.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/BarFactory.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Factory0.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/FooFactory1Main.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/BarFactory2.class")).isFalse()

    // Include with Regex and normal pattern
    relocator =
      SimpleRelocator(
        "org.foo",
        includes = listOf("%regex[org/foo/.*Factory[0-9].*]", "org.foo.public.*"),
      )
    assertThat(relocator.canRelocatePath("org/foo/Factory1.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/public/Bar.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Factory.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/R.class")).isFalse()
  }

  @Test
  fun canRelocateClass() {
    var relocator = SimpleRelocator("org.foo")
    assertThat(relocator.canRelocateClass("org.foo.Class")).isTrue()
    assertThat(relocator.canRelocateClass("org.foo.bar.Class")).isTrue()
    assertThat(relocator.canRelocateClass("com.foo.bar.Class")).isFalse()
    assertThat(relocator.canRelocateClass("org.Foo.Class")).isFalse()

    relocator =
      SimpleRelocator(
        "org.foo",
        excludes =
          listOf(
            "org.foo.Excluded",
            "org.foo.public.*",
            "org.foo.recurse.**",
            "org.foo.Public*Stuff",
          ),
      )
    assertThat(relocator.canRelocateClass("org.foo.Class")).isTrue()
    assertThat(relocator.canRelocateClass("org.foo.excluded")).isTrue()
    assertThat(relocator.canRelocateClass("org.foo.Excluded")).isFalse()
    assertThat(relocator.canRelocateClass("org.foo.public")).isFalse()
    assertThat(relocator.canRelocateClass("org.foo.public.Class")).isFalse()
    assertThat(relocator.canRelocateClass("org.foo.public.sub")).isFalse()
    assertThat(relocator.canRelocateClass("org.foo.public.sub.Class")).isTrue()
    assertThat(relocator.canRelocateClass("org.foo.publicRELOC.Class")).isTrue()
    assertThat(relocator.canRelocateClass("org.foo.PrivateStuff")).isTrue()
    assertThat(relocator.canRelocateClass("org.foo.PublicStuff")).isFalse()
    assertThat(relocator.canRelocateClass("org.foo.PublicUtilStuff")).isFalse()
    assertThat(relocator.canRelocateClass("org.foo.recurse")).isFalse()
    assertThat(relocator.canRelocateClass("org.foo.recurse.Class")).isFalse()
    assertThat(relocator.canRelocateClass("org.foo.recurse.sub")).isFalse()
    assertThat(relocator.canRelocateClass("org.foo.recurse.sub.Class")).isFalse()
  }

  @Test
  fun canRelocateRawString() {
    var relocator = SimpleRelocator("org/foo", rawString = true)
    assertThat(relocator.canRelocatePath("(I)org/foo/bar/Class;")).isTrue()

    relocator = SimpleRelocator("^META-INF/org.foo.xml$", rawString = true)
    assertThat(relocator.canRelocatePath("META-INF/org.foo.xml")).isTrue()
  }

  @Test
  fun canRelocateAbsClassPath() {
    val relocator = SimpleRelocator("org.apache.velocity", "org.apache.momentum")
    assertThat(relocator.relocatePath("/org/apache/velocity/mass.properties"))
      .isEqualTo("/org/apache/momentum/mass.properties")
  }

  @Test
  fun canRelocateAbsClassPathWithExcludes() {
    val relocator =
      SimpleRelocator(
        "org/apache/velocity",
        "org/apache/momentum",
        excludes = listOf("org/apache/velocity/excluded/*"),
      )
    assertThat(relocator.canRelocatePath("/org/apache/velocity/mass.properties")).isTrue()
    assertThat(relocator.canRelocatePath("org/apache/velocity/mass.properties")).isTrue()
    assertThat(relocator.canRelocatePath("/org/apache/velocity/excluded/mass.properties")).isFalse()
    assertThat(relocator.canRelocatePath("org/apache/velocity/excluded/mass.properties")).isFalse()
  }

  @Test
  fun canRelocateAbsClassPathWithIncludes() {
    val relocator =
      SimpleRelocator(
        "org/apache/velocity",
        "org/apache/momentum",
        includes = listOf("org/apache/velocity/included/*"),
      )
    assertThat(relocator.canRelocatePath("/org/apache/velocity/mass.properties")).isFalse()
    assertThat(relocator.canRelocatePath("org/apache/velocity/mass.properties")).isFalse()
    assertThat(relocator.canRelocatePath("/org/apache/velocity/included/mass.properties")).isTrue()
    assertThat(relocator.canRelocatePath("org/apache/velocity/included/mass.properties")).isTrue()
  }

  @Test
  fun relocatePath() {
    var relocator = SimpleRelocator("org.foo")
    assertThat(relocator.relocatePath("org/foo/bar/Class.class"))
      .isEqualTo("hidden/org/foo/bar/Class.class")
    // Post-pattern case.
    assertThat(relocator.relocatePath("org/foosssssss/bar/Class.class"))
      .isEqualTo("hidden/org/foosssssss/bar/Class.class")

    relocator = SimpleRelocator("org.foo", "private.stuff")
    assertThat(relocator.relocatePath("org/foo/bar/Class.class"))
      .isEqualTo("private/stuff/bar/Class.class")
  }

  @Test
  fun relocateClass() {
    var relocator = SimpleRelocator("org.foo")
    assertThat(relocator.relocateClass("org.foo.bar.Class")).isEqualTo("hidden.org.foo.bar.Class")

    relocator = SimpleRelocator("org.foo", "private.stuff")
    assertThat(relocator.relocateClass("org.foo.bar.Class")).isEqualTo("private.stuff.bar.Class")
  }

  @Test
  fun relocateRawString() {
    var relocator = SimpleRelocator("Lorg/foo", "Lhidden/org/foo", rawString = true)
    assertThat(relocator.relocatePath("(I)Lorg/foo/bar/Class;"))
      .isEqualTo("(I)Lhidden/org/foo/bar/Class;")

    relocator =
      SimpleRelocator("^META-INF/org.foo.xml$", "META-INF/hidden.org.foo.xml", rawString = true)
    assertThat(relocator.relocatePath("META-INF/org.foo.xml"))
      .isEqualTo("META-INF/hidden.org.foo.xml")
  }

  @Test
  fun relocateMavenFiles() {
    val relocator =
      SimpleRelocator(
        "META-INF/maven",
        "META-INF/shade/maven",
        excludes = listOf("META-INF/maven/com.foo.bar/artifactId/pom.*"),
      )
    assertThat(relocator.canRelocatePath("META-INF/maven/com.foo.bar/artifactId/pom.properties"))
      .isFalse()
    assertThat(relocator.canRelocatePath("META-INF/maven/com.foo.bar/artifactId/pom.xml")).isFalse()
    assertThat(relocator.canRelocatePath("META-INF/maven/com/foo/bar/artifactId/pom.properties"))
      .isTrue()
    assertThat(relocator.canRelocatePath("META-INF/maven/com/foo/bar/artifactId/pom.xml")).isTrue()
    assertThat(relocator.canRelocatePath("META-INF/maven/com-foo-bar/artifactId/pom.properties"))
      .isTrue()
    assertThat(relocator.canRelocatePath("META-INF/maven/com-foo-bar/artifactId/pom.xml")).isTrue()
  }

  @Test
  fun canRelocateExcludedSourceFile() {
    val relocator =
      SimpleRelocator(
        "org.foo",
        excludes =
          listOf(
            "org/apache/iceberg/spark/parquet/**",
            "org/apache/spark/sql/execution/datasources/parquet/**",
          ),
      )
    assertThat(
        relocator.canRelocatePath("org/apache/iceberg/spark/parquet/SparkNativeParquet.class")
      )
      .isFalse()
    assertThat(
        relocator.canRelocatePath("org/apache/iceberg/spark/parquet/SparkNativeParquet$.class")
      )
      .isFalse()
    assertThat(
        relocator.canRelocatePath("org/apache/spark/sql/execution/datasources/parquet/v1.class")
      )
      .isFalse()
    assertThat(relocator.canRelocatePath("org/foo/Class.class")).isTrue()
  }

  @Test
  fun canRelocateExcludedSourceFileWithRegex() {
    val relocator =
      SimpleRelocator(
        "org.foo",
        excludes = listOf("%regex[org/apache/iceberg/.*]", "%regex[org/apache/spark/.*]"),
      )
    assertThat(
        relocator.canRelocatePath("org/apache/iceberg/spark/parquet/SparkNativeParquet.class")
      )
      .isFalse()
    assertThat(
        relocator.canRelocatePath("org/apache/iceberg/spark/parquet/SparkNativeParquet$.class")
      )
      .isFalse()
    assertThat(
        relocator.canRelocatePath("org/apache/spark/sql/execution/datasources/parquet/v1.class")
      )
      .isFalse()
    assertThat(relocator.canRelocatePath("org/foo/Class.class")).isTrue()
  }

  @Test
  fun canRelocateIncludedSourceFile() {
    val relocator =
      SimpleRelocator(
        includes =
          listOf(
            "org/apache/iceberg/spark/parquet/**",
            "org/apache/spark/sql/execution/datasources/parquet/**",
          )
      )
    assertThat(
        relocator.canRelocatePath("org/apache/iceberg/spark/parquet/SparkNativeParquet.class")
      )
      .isTrue()
    assertThat(
        relocator.canRelocatePath("org/apache/iceberg/spark/parquet/SparkNativeParquet$.class")
      )
      .isTrue()
    assertThat(
        relocator.canRelocatePath("org/apache/spark/sql/execution/datasources/parquet/v1.class")
      )
      .isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Class.class")).isFalse()
  }

  @Test
  fun canRelocateIncludedSourceFileWithRegex() {
    val relocator =
      SimpleRelocator(
        includes = listOf("%regex[org/apache/iceberg/.*]", "%regex[org/apache/spark/.*]")
      )
    assertThat(
        relocator.canRelocatePath("org/apache/iceberg/spark/parquet/SparkNativeParquet.class")
      )
      .isTrue()
    assertThat(
        relocator.canRelocatePath("org/apache/iceberg/spark/parquet/SparkNativeParquet$.class")
      )
      .isTrue()
    assertThat(
        relocator.canRelocatePath("org/apache/spark/sql/execution/datasources/parquet/v1.class")
      )
      .isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Class.class")).isFalse()
  }

  @Test
  fun relocateSourceWithExcludesRaw() {
    val relocator =
      SimpleRelocator(
        "org.apache.maven",
        "com.acme.maven",
        listOf("foo.bar", "zot.baz"),
        listOf("irrelevant.exclude", "org.apache.maven.exclude1", "org.apache.maven.sub.exclude2"),
        true,
      )
    assertThat(relocator.applyToSourceContent(sourceFile)).isEqualTo(sourceFile)
  }

  @Test
  fun relocateSourceWithExcludes() {
    // Main relocator with excludes
    val relocator =
      SimpleRelocator(
        "org.apache.maven",
        "com.acme.maven",
        excludes =
          listOf(
            "irrelevant.exclude",
            "org.apache.maven.exclude1",
            "org.apache.maven.sub.exclude2",
          ),
      )
    // Make sure not to replace variables 'io' and 'ioInput', package 'java.io'
    val ioRelocator = SimpleRelocator("io", "shaded.io")
    // Check corner case which was not working in PR #100
    val asmRelocator = SimpleRelocator("org.objectweb.asm", "aj.org.objectweb.asm")
    // Make sure not to replace 'foo' package by path-like 'shaded/foo'
    val fooRelocator = SimpleRelocator("foo", "shaded.foo", excludes = listOf("foo.bar"))
    assertThat(
        fooRelocator.applyToSourceContent(
          asmRelocator.applyToSourceContent(
            ioRelocator.applyToSourceContent(relocator.applyToSourceContent(sourceFile))
          )
        )
      )
      .isEqualTo(relocatedFile)
  }

  @Test
  fun relocateSourceWithDslExcludeAndInclude() {
    val relocatorExclude = SimpleRelocator("org.apache.maven", "com.acme.maven")
    relocatorExclude.exclude("org.apache.maven.exclude1.*")
    val inputExclude =
      """
      |import org.apache.maven.hello.World;
      |import org.apache.maven.exclude1.Ex1;
      """
        .trimMargin()
    val expectedExclude =
      """
      |import com.acme.maven.hello.World;
      |import org.apache.maven.exclude1.Ex1;
      """
        .trimMargin()
    assertThat(relocatorExclude.applyToSourceContent(inputExclude)).isEqualTo(expectedExclude)

    val relocatorInclude = SimpleRelocator("org.apache.maven", "com.acme.maven")
    relocatorInclude.include("org.apache.maven.hello.*")
    val inputInclude =
      """
      |import org.apache.maven.hello.World;
      |import org.apache.maven.other.Other;
      """
        .trimMargin()
    val expectedInclude =
      """
      |import com.acme.maven.hello.World;
      |import org.apache.maven.other.Other;
      """
        .trimMargin()
    assertThat(relocatorInclude.applyToSourceContent(inputInclude)).isEqualTo(expectedInclude)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sourceRelocationProvider")
  fun relocateSourceContent(
    name: String,
    relocator: SimpleRelocator,
    input: String,
    expected: String,
  ) {
    assertThat(relocator.applyToSourceContent(input)).isEqualTo(expected)
  }

  private companion object {
    @JvmStatic
    fun sourceRelocationProvider(): List<Arguments> =
      listOf(
        Arguments.of(
          "resource paths with leading slash",
          SimpleRelocator("org.foo", "shaded.org.foo"),
          """
          |String a = "org/foo/a.properties";
          |String b = "/org/foo/b.properties";
          |getClass().getResource("/org/foo/c.properties");
          |String d = '/org/foo/d.properties';
          |String e = "com/example/org/foo/e.properties";
          |String f = "../org/foo/f.properties";
          |String g = "my-lib/org/foo/g.properties";
          """
            .trimMargin(),
          """
          |String a = "shaded/org/foo/a.properties";
          |String b = "/shaded/org/foo/b.properties";
          |getClass().getResource("/shaded/org/foo/c.properties");
          |String d = '/shaded/org/foo/d.properties';
          |String e = "com/example/org/foo/e.properties";
          |String f = "../org/foo/f.properties";
          |String g = "my-lib/org/foo/g.properties";
          """
            .trimMargin(),
        ),
        Arguments.of(
          "prefix collision with included class",
          SimpleRelocator(
            "org.example",
            "relocated.org.example",
            includes = listOf("org.example.In"),
          ),
          """
          |import org.example.In;
          |import org.example.Input;
          |import org.example.In.Nested;
          |
          |public class Test {
          |  org.example.In a;
          |  org.example.Input b;
          |}
          """
            .trimMargin(),
          """
          |import relocated.org.example.In;
          |import org.example.Input;
          |import relocated.org.example.In.Nested;
          |
          |public class Test {
          |  relocated.org.example.In a;
          |  org.example.Input b;
          |}
          """
            .trimMargin(),
        ),
        Arguments.of(
          "includes only",
          SimpleRelocator(
            "org.apache.maven",
            "com.acme.maven",
            includes = listOf("org.apache.maven.hello.*", "org.apache.maven.In"),
          ),
          """
          |package org.apache.maven.hello;
          |import org.apache.maven.hello.World;
          |import org.apache.maven.other.Other;
          |import org.apache.maven.In;
          |import org.apache.maven.NotIn;
          """
            .trimMargin(),
          """
          |package com.acme.maven.hello;
          |import com.acme.maven.hello.World;
          |import org.apache.maven.other.Other;
          |import com.acme.maven.In;
          |import org.apache.maven.NotIn;
          """
            .trimMargin(),
        ),
        Arguments.of(
          "qualified names in expressions and type annotations",
          SimpleRelocator("com.example", "shaded.example"),
          """
          |class Main {
          |  void method() {
          |    return com.example.Factory.create();
          |  }
          |  com.example.Type value = new com.example.Type();
          |  val typed: com.example.Type = com.example.Factory.create()
          |}
          """
            .trimMargin(),
          """
          |class Main {
          |  void method() {
          |    return shaded.example.Factory.create();
          |  }
          |  shaded.example.Type value = new shaded.example.Type();
          |  val typed: shaded.example.Type = shaded.example.Factory.create()
          |}
          """
            .trimMargin(),
        ),
        Arguments.of(
          "includes and excludes combined",
          SimpleRelocator(
            "com.example",
            "shaded.example",
            includes = listOf("com.example.used.*"),
            excludes = listOf("com.example.used.Excluded"),
          ),
          """
          |package com.example.used;
          |import com.example.used.Foo;
          |import com.example.used.Excluded;
          |import com.example.unused.Bar;
          """
            .trimMargin(),
          """
          |package shaded.example.used;
          |import shaded.example.used.Foo;
          |import com.example.used.Excluded;
          |import com.example.unused.Bar;
          """
            .trimMargin(),
        ),
        Arguments.of(
          "multi-language constructs in Kotlin, Groovy, Scala, and Java",
          SimpleRelocator("com.example", "shaded.example"),
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
            .trimMargin(),
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
            .trimMargin(),
        ),
      )

    val sourceFile =
      """
      |package org.apache.maven.hello;
      |package org.objectweb.asm;
      |
      |import foo.bar.Bar;
      |import zot.baz.Baz;
      |import org.apache.maven.exclude1.Ex1;
      |import org.apache.maven.exclude1.a.b.Ex1AB;
      |import org.apache.maven.sub.exclude2.Ex2;
      |import org.apache.maven.sub.exclude2.c.d.Ex2CD;
      |import org.apache.maven.In;
      |import org.apache.maven.e.InE;
      |import org.apache.maven.f.g.InFG;
      |import java.io.IOException;
      |
      |/**
      | * Also check out {@link org.apache.maven.hello.OtherClass} and {@link
      | * org.apache.maven.hello.YetAnotherClass}
      | */
      |public class MyClass {
      |  private org.apache.maven.exclude1.x.X myX;
      |  private org.apache.maven.h.H h;
      |  private String ioInput;
      |
      |  /** Javadoc, followed by default visibility method with fully qualified return type */
      |  org.apache.maven.MyReturnType doSomething( org.apache.maven.Bar bar, org.objectweb.asm.sub.Something something) {
      |    org.apache.maven.Bar bar;
      |    org.objectweb.asm.sub.Something something;
      |    String io, val;
      |    String noRelocation = "NoWordBoundaryXXXorg.apache.maven.In";
      |    String relocationPackage = "org.apache.maven.In";
      |    String relocationPath = "org/apache/maven/In";
      |  }
      |}
      """
        .trimMargin()

    val relocatedFile =
      """
      |package com.acme.maven.hello;
      |package aj.org.objectweb.asm;
      |
      |import foo.bar.Bar;
      |import zot.baz.Baz;
      |import org.apache.maven.exclude1.Ex1;
      |import org.apache.maven.exclude1.a.b.Ex1AB;
      |import org.apache.maven.sub.exclude2.Ex2;
      |import org.apache.maven.sub.exclude2.c.d.Ex2CD;
      |import com.acme.maven.In;
      |import com.acme.maven.e.InE;
      |import com.acme.maven.f.g.InFG;
      |import java.io.IOException;
      |
      |/**
      | * Also check out {@link com.acme.maven.hello.OtherClass} and {@link
      | * com.acme.maven.hello.YetAnotherClass}
      | */
      |public class MyClass {
      |  private org.apache.maven.exclude1.x.X myX;
      |  private com.acme.maven.h.H h;
      |  private String ioInput;
      |
      |  /** Javadoc, followed by default visibility method with fully qualified return type */
      |  com.acme.maven.MyReturnType doSomething( com.acme.maven.Bar bar, aj.org.objectweb.asm.sub.Something something) {
      |    com.acme.maven.Bar bar;
      |    aj.org.objectweb.asm.sub.Something something;
      |    String io, val;
      |    String noRelocation = "NoWordBoundaryXXXorg.apache.maven.In";
      |    String relocationPackage = "com.acme.maven.In";
      |    String relocationPath = "com/acme/maven/In";
      |  }
      |}
      """
        .trimMargin()
  }
}
