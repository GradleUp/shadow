package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import de.infix.testBalloon.framework.core.testSuite

val ServiceFileTransformerTests by testSuite {
  runTests(::ServiceFileTransformerTest)
}

/**
 * Modified from
 * [org.apache.maven.plugins.shade.resource.ServiceResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ServiceResourceTransformerTest.java).
 */
private class ServiceFileTransformerTest : BaseTransformerTest<ServiceFileTransformer>() {
  fun canTransformResource(path: String, exclude: Boolean, expected: Boolean) =
    with(transformer) {
      if (exclude) {
        exclude(path)
      }
      assertThat(canTransformResource(path)).isEqualTo(expected)
    }

  fun transformServiceFile(path: String, input1: String, input2: String, output: String) =
    with(transformer) {
      if (canTransformResource(path)) {
        transform(textContext(path, input1))
        transform(textContext(path, input2))
      }

      assertThat(hasTransformedResource()).isTrue()
      val entry = serviceEntries.getValue(path).joinToString("\n")
      assertThat(entry).isEqualTo(output)
    }

  fun excludesGroovyExtensionModuleDescriptorFilesByDefault() {
    val element = "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
    assertThat(transformer.canTransformResource(element)).isFalse()
  }

  fun canTransformAlternateResource() =
    with(transformer) {
      path = "foo/bar"
      assertThat(canTransformResource("foo/bar/moo/goo/Zoo")).isTrue()
      assertThat(canTransformResource("META-INF/services/Zoo")).isFalse()
    }

  fun relocatedClasses() =
    with(transformer) {
      val relocator = SimpleRelocator("org.foo", "borg.foo")
      var content = "org.foo.Service\n"
      var contentResource = "META-INF/services/org.foo.something.another"

      transform(textContext(contentResource, content, relocator))

      content = "org.blah.Service\n"
      contentResource = "META-INF/services/org.something.another"

      transform(textContext(contentResource, content, relocator))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }

      val transformedContent = JarPath(tempJar).use { it.getContent(contentResource) }
      assertThat(transformedContent).isEqualTo("org.blah.Service")
    }

  fun serviceEntriesAreAppendedAndPreservedOrder() =
    with(transformer) {
      val relocator = SimpleRelocator("org.foo", "borg.foo")
      var content = "org.foo.Service\n"
      var contentResource = "META-INF/services/org.something.another"

      transform(textContext(contentResource, content, relocator))

      content = "org.blah.Service\n"
      contentResource = "META-INF/services/org.something.another"

      transform(textContext(contentResource, content, relocator))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }

      val transformedContent = JarPath(tempJar).use { it.getContent(contentResource) }
      assertThat(transformedContent).isEqualTo("borg.foo.Service\norg.blah.Service")
    }

  companion object {
    val resourceProvider =
      listOf(
        // path, exclude, expected
        Triple("META-INF/services/java.sql.Driver", false, true),
        Triple("META-INF/services/io.dropwizard.logging.AppenderFactory", false, true),
        Triple("META-INF/services/org.apache.maven.Shade", true, false),
        Triple("META-INF/services/foo/bar/moo.goo.Zoo", false, true),
        Triple("foo/bar.properties", false, false),
        Triple("foo.props", false, false),
      )

    val serviceFileProvider =
      listOf(
        // path, input1, input2, output
        Tuple4("META-INF/services/com.acme.Foo", "foo", "bar", "foo\nbar"),
        Tuple4("META-INF/services/com.acme.Bar", "foo\nbar", "zoo", "foo\nbar\nzoo"),
      )
  }
}

data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
