package com.github.jengelman.gradle.plugins.shadow.relocation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.relocation.SimpleRelocatorTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/relocation/SimpleRelocatorTest.java).
 *
 * @author John Engelman
 */
class SimpleRelocatorTest {

  @Test
  fun testCanRelocatePath() {
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

    relocator = SimpleRelocator(
      "org.foo",
      excludes = listOf("org.foo.Excluded", "org.foo.public.*", "org.foo.recurse.**", "org.foo.Public*Stuff"),
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
  }

  @Test
  fun testCanRelocatePathWithRegex() {
    // Include with Regex
    var relocator = SimpleRelocator("org.foo", includes = listOf("%regex[org/foo/R(\\\$.*)?\$]"))
    assertThat(relocator.canRelocatePath("org/foo/R.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/R\$string.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/R\$layout.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Recording/R.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/Recording.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/bar/R\$string.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/R.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/R\$string.class")).isFalse()

    // Exclude with Regex
    relocator = SimpleRelocator("org.foo")
    relocator.exclude("%regex[org/foo/.*Factory[0-9].*]")
    assertThat(relocator.canRelocatePath("org/foo/Factory.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/FooFactoryMain.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/BarFactory.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Factory0.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/FooFactory1Main.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/BarFactory2.class")).isFalse()

    // Include with Regex and normal pattern
    relocator = SimpleRelocator(
      "org.foo",
      includes = listOf("%regex[org/foo/.*Factory[0-9].*]", "org.foo.public.*"),
    )
    assertThat(relocator.canRelocatePath("org/foo/Factory1.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/public/Bar.class")).isTrue()
    assertThat(relocator.canRelocatePath("org/foo/Factory.class")).isFalse()
    assertThat(relocator.canRelocatePath("org/foo/R.class")).isFalse()
  }

  @Test
  fun testCanRelocateClass() {
    var relocator = SimpleRelocator("org.foo")
    assertThat(relocator.canRelocateClass("org.foo.Class")).isTrue()
    assertThat(relocator.canRelocateClass("org.foo.bar.Class")).isTrue()
    assertThat(relocator.canRelocateClass("com.foo.bar.Class")).isFalse()
    assertThat(relocator.canRelocateClass("org.Foo.Class")).isFalse()

    relocator = SimpleRelocator(
      "org.foo",
      excludes = listOf("org.foo.Excluded", "org.foo.public.*", "org.foo.recurse.**", "org.foo.Public*Stuff"),
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
  fun testCanRelocateRawString() {
    var relocator = SimpleRelocator("org/foo", _rawString = true)
    assertThat(relocator.canRelocatePath("(I)org/foo/bar/Class")).isTrue()

    relocator = SimpleRelocator("^META-INF/org.foo.xml\$", _rawString = true)
    assertThat(relocator.canRelocatePath("META-INF/org.foo.xml")).isTrue()
  }

  @Test
  fun testCanRelocateAbsClassPath() {
    val relocator = SimpleRelocator("org.apache.velocity", "org.apache.momentum")
    assertThat(relocator.relocatePath(pathContext("/org/apache/velocity/mass.properties")))
      .isEqualTo("/org/apache/momentum/mass.properties")
  }

  @Test
  fun testRelocatePath() {
    var relocator = SimpleRelocator("org.foo")
    assertThat(relocator.relocatePath(pathContext("org/foo/bar/Class.class")))
      .isEqualTo("hidden/org/foo/bar/Class.class")

    relocator = SimpleRelocator("org.foo", "private.stuff")
    assertThat(relocator.relocatePath(pathContext("org/foo/bar/Class.class")))
      .isEqualTo("private/stuff/bar/Class.class")
  }

  @Test
  fun testRelocateClass() {
    var relocator = SimpleRelocator("org.foo")
    assertThat(relocator.relocateClass(classContext()))
      .isEqualTo("hidden.org.foo.bar.Class")

    relocator = SimpleRelocator("org.foo", "private.stuff")
    assertThat(relocator.relocateClass(classContext()))
      .isEqualTo("private.stuff.bar.Class")
  }

  @Test
  fun testRelocateRawString() {
    var relocator = SimpleRelocator("Lorg/foo", "Lhidden/org/foo", _rawString = true)
    assertThat(relocator.relocatePath(pathContext("(I)Lorg/foo/bar/Class")))
      .isEqualTo("(I)Lhidden/org/foo/bar/Class")

    relocator = SimpleRelocator("^META-INF/org.foo.xml\$", "META-INF/hidden.org.foo.xml", _rawString = true)
    assertThat(relocator.relocatePath(pathContext("META-INF/org.foo.xml")))
      .isEqualTo("META-INF/hidden.org.foo.xml")
  }

  private fun pathContext(path: String): RelocatePathContext {
    return RelocatePathContext.builder().path(path).build()
  }

  private fun classContext(className: String = "org.foo.bar.Class"): RelocateClassContext {
    return RelocateClassContext.builder().className(className).build()
  }

  @Test
  fun testCanRelocateExcludedSourceFile() {
    val relocator = SimpleRelocator("org.foo")
    relocator.excludeSources("org/apache/iceberg/spark/parquet/**")
    relocator.excludeSources("org/apache/spark/sql/execution/datasources/parquet/**")

    assertThat(relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet.class")).isFalse()
    assertThat(relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet\$.class")).isFalse()
    assertThat(relocator.canRelocateSourceFile("org/apache/spark/sql/execution/datasources/parquet/v1.class")).isFalse()
    assertThat(relocator.canRelocateSourceFile("org/foo/Class.class")).isTrue()
  }

  @Test
  fun testCanRelocateExcludedSourceFileWithRegex() {
    val relocator = SimpleRelocator("org.foo")
    relocator.excludeSources("%regex[org/apache/iceberg/.*]")
    relocator.excludeSources("%regex[org/apache/spark/.*]")

    assertThat(relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet.class")).isFalse()
    assertThat(relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet\$.class")).isFalse()
    assertThat(relocator.canRelocateSourceFile("org/apache/spark/sql/execution/datasources/parquet/v1.class")).isFalse()
    assertThat(relocator.canRelocateSourceFile("org/foo/Class.class")).isTrue()
  }

  @Test
  fun testCanRelocateIncludedSourceFile() {
    val relocator = SimpleRelocator("org.foo")
    relocator.includeSources("org/apache/iceberg/spark/parquet/**")
    relocator.includeSources("org/apache/spark/sql/execution/datasources/parquet/**")

    assertThat(relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet.class")).isTrue()
    assertThat(relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet\$.class")).isTrue()
    assertThat(relocator.canRelocateSourceFile("org/apache/spark/sql/execution/datasources/parquet/v1.class")).isTrue()
    assertThat(relocator.canRelocateSourceFile("org/foo/Class.class")).isFalse()
  }

  @Test
  fun testCanRelocateIncludedSourceFileWithRegex() {
    val relocator = SimpleRelocator("org.foo")
    relocator.includeSources("%regex[org/apache/iceberg/.*]")
    relocator.includeSources("%regex[org/apache/spark/.*]")

    assertThat(relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet.class")).isTrue()
    assertThat(relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet\$.class")).isTrue()
    assertThat(relocator.canRelocateSourceFile("org/apache/spark/sql/execution/datasources/parquet/v1.class")).isTrue()
    assertThat(relocator.canRelocateSourceFile("org/foo/Class.class")).isFalse()
  }
}
