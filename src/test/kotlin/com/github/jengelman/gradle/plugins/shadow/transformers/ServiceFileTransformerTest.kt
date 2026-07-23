package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Modified from
 * [org.apache.maven.plugins.shade.resource.ServiceResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ServiceResourceTransformerTest.java).
 */
class ServiceFileTransformerTest : BaseTransformerTest<ServiceFileTransformer>() {
  private lateinit var tempJar: Path

  @BeforeEach
  override fun beforeEach() {
    super.beforeEach()
    tempJar = createTempFile("shade.", ".jar")
  }

  @AfterEach
  fun afterEach() {
    tempJar.deleteExisting()
  }

  @ParameterizedTest
  @MethodSource("resourceProvider")
  fun canTransformResource(path: String, exclude: Boolean, expected: Boolean) =
    with(transformer) {
      if (exclude) {
        exclude(path)
      }
      assertThat(canTransformResource(path)).isEqualTo(expected)
    }

  @ParameterizedTest
  @MethodSource("serviceFileProvider")
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

  @Test
  fun excludesGroovyExtensionModuleDescriptorFilesByDefault() {
    val element = "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
    assertThat(transformer.canTransformResource(element)).isFalse()
  }

  @Test
  fun canTransformAlternateResource() =
    with(transformer) {
      path = "foo/bar"
      assertThat(canTransformResource("foo/bar/moo/goo/Zoo")).isTrue()
      assertThat(canTransformResource("META-INF/services/Zoo")).isFalse()
    }

  @Test
  fun relocatedClasses() =
    with(ServiceFileTransformer()) {
      val relocator = SimpleRelocator("org.foo", "borg.foo", excludes = listOf("org.foo.exclude.*"))
      val content = "org.foo.Service\norg.foo.exclude.OtherService\n"
      val contentResource = "META-INF/services/org.foo.something.another"
      val contentResourceShaded = "META-INF/services/borg.foo.something.another"

      transform(textContext(contentResource, content, relocator))

      tempJar.outputStream().zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }

      val transformedContent = JarPath(tempJar).use { it.getContent(contentResourceShaded) }
      assertThat(transformedContent).isEqualTo("borg.foo.Service\norg.foo.exclude.OtherService")
    }

  @Test
  fun mergeRelocatedFiles() =
    with(ServiceFileTransformer()) {
      val relocator = SimpleRelocator("org.foo", "borg.foo", excludes = listOf("org.foo.exclude.*"))
      val content = "org.foo.Service\norg.foo.exclude.OtherService\n"
      val contentResource = "META-INF/services/org.foo.something.another"
      val contentResourceShaded = "META-INF/services/borg.foo.something.another"

      transform(textContext(contentResource, content, relocator))
      transform(textContext(contentResourceShaded, content, relocator))

      tempJar.outputStream().zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }

      val transformedContent = JarPath(tempJar).use { it.getContent(contentResourceShaded) }
      assertThat(transformedContent).isEqualTo("borg.foo.Service\norg.foo.exclude.OtherService")
    }

  @Test
  fun concatenationAppliedMultipleTimes() =
    with(ServiceFileTransformer()) {
      val relocator = SimpleRelocator("org.eclipse", "org.eclipse1234")
      val content = "org.eclipse.osgi.launch.EquinoxFactory\n"
      val contentResource = "META-INF/services/org.osgi.framework.launch.FrameworkFactory"

      transform(textContext(contentResource, content, relocator))

      tempJar.outputStream().zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }

      val transformedContent = JarPath(tempJar).use { it.getContent(contentResource) }
      assertThat(transformedContent).isEqualTo("org.eclipse1234.osgi.launch.EquinoxFactory")
    }

  @Test
  fun concatenation() =
    with(ServiceFileTransformer()) {
      val relocator = SimpleRelocator("org.foo", "borg.foo")
      var content = "org.foo.Service\n"
      var contentResource = "META-INF/services/org.something.another"
      transform(textContext(contentResource, content, relocator))

      content = "org.blah.Service\n"
      contentResource = "META-INF/services/org.something.another"

      transform(textContext(contentResource, content, relocator))

      tempJar.outputStream().zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }

      val transformedContent = JarPath(tempJar).use { it.getContent(contentResource) }
      assertThat(transformedContent).isEqualTo("borg.foo.Service\norg.blah.Service")
    }

  private companion object {
    @JvmStatic
    fun resourceProvider() =
      listOf(
        // path, exclude, expected
        Arguments.of("META-INF/services/java.sql.Driver", false, true),
        Arguments.of("META-INF/services/io.dropwizard.logging.AppenderFactory", false, true),
        Arguments.of("META-INF/services/org.apache.maven.Shade", true, false),
        Arguments.of("META-INF/services/foo/bar/moo.goo.Zoo", false, true),
        Arguments.of("foo/bar.properties", false, false),
        Arguments.of("foo.props", false, false),
      )

    @JvmStatic
    fun serviceFileProvider() =
      listOf(
        // path, input1, input2, output
        Arguments.of("META-INF/services/com.acme.Foo", "foo", "bar", "foo\nbar"),
        Arguments.of("META-INF/services/com.acme.Bar", "foo\nbar", "zoo", "foo\nbar\nzoo"),
      )
  }
}
