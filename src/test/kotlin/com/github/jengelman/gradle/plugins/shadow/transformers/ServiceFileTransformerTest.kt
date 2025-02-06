package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.util.SimpleRelocator
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream
import org.apache.commons.io.IOUtils
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ServiceFileTransformerTest : BaseTransformerTest<ServiceFileTransformer>() {
  private lateinit var tempJar: Path

  @BeforeEach
  override fun setup() {
    super.setup()
    tempJar = createTempFile("shade.", ".jar")
  }

  @AfterEach
  fun cleanup() {
    tempJar.deleteExisting()
  }

  @ParameterizedTest
  @MethodSource("resourceProvider")
  fun canTransformResource(path: String, exclude: Boolean, expected: Boolean) {
    if (exclude) {
      transformer.exclude(path)
    }
    assertThat(transformer.canTransformResource(path)).isEqualTo(expected)
  }

  @ParameterizedTest
  @MethodSource("serviceFileProvider")
  fun transformServiceFile(path: String, input1: String, input2: String, output: String) {
    if (transformer.canTransformResource(path)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.hasTransformedResource()).isTrue()
    val entry = transformer.serviceEntries.getValue(path).joinToString("\n")
    assertThat(entry).isEqualTo(output)
  }

  @Test
  fun excludesGroovyExtensionModuleDescriptorFilesByDefault() {
    val element = "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
    assertThat(transformer.canTransformResource(element)).isFalse()
  }

  @Test
  fun canTransformAlternateResource() {
    transformer.path = "foo/bar"
    assertThat(transformer.canTransformResource("foo/bar/moo/goo/Zoo")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/services/Zoo")).isFalse()
  }

  @Test
  fun relocatedClasses() {
    val relocator = SimpleRelocator("org.foo", "borg.foo", excludes = listOf("org.foo.exclude.*"))
    val content = "org.foo.Service\norg.foo.exclude.OtherService\n"
    val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
    val contentStream = ByteArrayInputStream(contentBytes)
    val contentResource = "META-INF/services/org.foo.something.another"
    val contentResourceShaded = "META-INF/services/borg.foo.something.another"

    val transformer = ServiceFileTransformer()
    transformer.transform(context(contentResource, contentStream, setOf(relocator)))

    tempJar.outputStream().use { fos ->
      ZipOutputStream(fos).use { zos ->
        transformer.modifyOutputStream(zos, false)
      }
    }

    val jarFile = ZipFile(tempJar.toFile())
    val jarEntry = jarFile.getEntry(contentResourceShaded)
    assertThat(jarEntry).isNotNull()
    jarFile.getInputStream(jarEntry).use { entryStream ->
      val xformedContent = IOUtils.toString(entryStream, StandardCharsets.UTF_8)
      assertThat(xformedContent).isEqualTo("borg.foo.Service\norg.foo.exclude.OtherService")
    }
  }

  @Test
  fun mergeRelocatedFiles() {
    val relocator = SimpleRelocator("org.foo", "borg.foo", excludes = listOf("org.foo.exclude.*"))
    val content = "org.foo.Service\norg.foo.exclude.OtherService\n"
    val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
    val contentResource = "META-INF/services/org.foo.something.another"
    val contentResourceShaded = "META-INF/services/borg.foo.something.another"

    val transformer = ServiceFileTransformer()
    transformer.transform(context(contentResource, ByteArrayInputStream(contentBytes), setOf(relocator)))
    transformer.transform(context(contentResourceShaded, ByteArrayInputStream(contentBytes), setOf(relocator)))

    tempJar.outputStream().use { fos ->
      ZipOutputStream(fos).use { zos ->
        transformer.modifyOutputStream(zos, false)
      }
    }

    val jarFile = ZipFile(tempJar.toFile())
    val jarEntry = jarFile.getEntry(contentResourceShaded)
    assertThat(jarEntry).isNotNull()
    jarFile.getInputStream(jarEntry).use { entryStream ->
      val xformedContent = IOUtils.toString(entryStream, StandardCharsets.UTF_8)
      assertThat(xformedContent).isEqualTo("borg.foo.Service\norg.foo.exclude.OtherService")
    }
  }

  @Test
  fun concatenationAppliedMultipleTimes() {
    val relocator = SimpleRelocator("org.eclipse", "org.eclipse1234")
    val content = "org.eclipse.osgi.launch.EquinoxFactory\n"
    val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
    val contentResource = "META-INF/services/org.osgi.framework.launch.FrameworkFactory"

    val transformer = ServiceFileTransformer()
    transformer.transform(context(contentResource, ByteArrayInputStream(contentBytes), setOf(relocator)))

    tempJar.outputStream().use { fos ->
      ZipOutputStream(fos).use { zos ->
        transformer.modifyOutputStream(zos, false)
      }
    }

    val jarFile = ZipFile(tempJar.toFile())
    val jarEntry = jarFile.getEntry(contentResource)
    assertThat(jarEntry).isNotNull()
    jarFile.getInputStream(jarEntry).use { entryStream ->
      val xformedContent = IOUtils.toString(entryStream, StandardCharsets.UTF_8)
      assertThat(xformedContent).isEqualTo("org.eclipse1234.osgi.launch.EquinoxFactory")
    }
  }

  @Test
  fun concatenation() {
    val relocator = SimpleRelocator("org.foo", "borg.foo")
    var content = "org.foo.Service\n"
    var contentBytes = content.toByteArray(StandardCharsets.UTF_8)
    var contentResource = "META-INF/services/org.something.another"

    val transformer = ServiceFileTransformer()
    transformer.transform(context(contentResource, ByteArrayInputStream(contentBytes), setOf(relocator)))

    content = "org.blah.Service\n"
    contentBytes = content.toByteArray(StandardCharsets.UTF_8)
    contentResource = "META-INF/services/org.something.another"

    transformer.transform(context(contentResource, ByteArrayInputStream(contentBytes), setOf(relocator)))

    tempJar.outputStream().use { fos ->
      ZipOutputStream(fos).use { zos ->
        transformer.modifyOutputStream(zos, false)
      }
    }

    val jarFile = ZipFile(tempJar.toFile())
    val jarEntry = jarFile.getEntry(contentResource)
    assertThat(jarEntry).isNotNull()
    jarFile.getInputStream(jarEntry).use { entryStream ->
      val xformedContent = IOUtils.toString(entryStream, StandardCharsets.UTF_8)
      val classes = xformedContent.split("\n")
      assertThat(classes.contains("org.blah.Service")).isTrue()
      assertThat(classes.contains("borg.foo.Service")).isTrue()
    }
  }

  private companion object {
    fun context(path: String, input: String): TransformerContext {
      return TransformerContext(path, input.byteInputStream(), stats = sharedStats)
    }

    fun context(path: String, inputStream: InputStream, relocators: Set<Relocator>): TransformerContext {
      return TransformerContext(path, inputStream, relocators)
    }

    @JvmStatic
    fun resourceProvider() = listOf(
      // path, exclude, expected
      Arguments.of("META-INF/services/java.sql.Driver", false, true),
      Arguments.of("META-INF/services/io.dropwizard.logging.AppenderFactory", false, true),
      Arguments.of("META-INF/services/org.apache.maven.Shade", true, false),
      Arguments.of("META-INF/services/foo/bar/moo.goo.Zoo", false, true),
      Arguments.of("foo/bar.properties", false, false),
      Arguments.of("foo.props", false, false),
    )

    @JvmStatic
    fun serviceFileProvider() = listOf(
      // path, input1, input2, output
      Arguments.of("META-INF/services/com.acme.Foo", "foo", "bar", "foo\nbar"),
      Arguments.of("META-INF/services/com.acme.Bar", "foo\nbar", "zoo", "foo\nbar\nzoo"),
    )
  }
}
