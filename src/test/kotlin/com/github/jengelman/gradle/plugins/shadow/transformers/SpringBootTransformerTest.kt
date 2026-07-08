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

class SpringBootTransformerTest : BaseTransformerTest<SpringBootTransformer>() {

  private lateinit var tempJar: Path

  @BeforeEach
  override fun beforeEach() {
    super.beforeEach()
    tempJar = createTempFile("springboot-unit.", ".jar")
  }

  @AfterEach
  fun afterEach() {
    tempJar.deleteExisting()
  }

  @ParameterizedTest
  @MethodSource("resourceProvider")
  fun canTransformResource(path: String, expected: Boolean) {
    assertThat(transformer.canTransformResource(path)).isEqualTo(expected)
  }

  @Test
  fun hasTransformedResource() {
    assertThat(transformer.hasTransformedResource()).isFalse()
    transformer.transform(textContext("META-INF/spring.factories", "foo=bar"))
    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun mergeProperties() {
    val path = "META-INF/spring.factories"
    transformer.transform(textContext(path, "foo=bar"))
    transformer.transform(textContext(path, "foo=spam"))

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val content = JarPath(tempJar).use { it.getContent(path) }
    assertThat(content.trim()).isEqualTo("foo=bar,spam")
  }

  @Test
  fun mergeImports() {
    val path = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
    transformer.transform(textContext(path, "com.example.ConfigA\ncom.example.ConfigB"))
    transformer.transform(textContext(path, "com.example.ConfigB\ncom.example.ConfigC"))

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val content = JarPath(tempJar).use { it.getContent(path) }
    assertThat(content.trim())
      .isEqualTo("com.example.ConfigA\ncom.example.ConfigB\ncom.example.ConfigC")
  }

  @Test
  fun relocateProperties() {
    val path = "META-INF/spring.factories"
    val relocator = SimpleRelocator("com.example", "shadow.example")

    transformer.transform(textContext(path, "com.example.Interface=com.example.Impl", relocator))

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val content = JarPath(tempJar).use { it.getContent(path) }
    assertThat(content.trim()).isEqualTo("shadow.example.Interface=shadow.example.Impl")
  }

  @Test
  fun relocateImports() {
    val path = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
    val relocator = SimpleRelocator("com.example", "shadow.example")

    transformer.transform(textContext(path, "com.example.ConfigA", relocator))

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val content = JarPath(tempJar).use { it.getContent(path) }
    assertThat(content.trim()).isEqualTo("shadow.example.ConfigA")
  }

  @Test
  fun mergeAutoconfigureMetadata() {
    val path = "META-INF/spring-autoconfigure-metadata.properties"
    transformer.transform(
      textContext(path, "com.example.Config.ConditionalOnClass=com.example.Class1")
    )
    transformer.transform(
      textContext(path, "com.example.Config.ConditionalOnClass=com.example.Class2")
    )

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val content = JarPath(tempJar).use { it.getContent(path) }
    assertThat(content.trim())
      .isEqualTo("com.example.Config.ConditionalOnClass=com.example.Class1,com.example.Class2")
  }

  @Test
  fun relocateAutoconfigureMetadata() {
    val path = "META-INF/spring-autoconfigure-metadata.properties"
    val relocator = SimpleRelocator("com.example", "shadow.example")
    transformer.transform(
      textContext(path, "com.example.Config.ConditionalOnClass=com.example.Class1", relocator)
    )

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val content = JarPath(tempJar).use { it.getContent(path) }
    assertThat(content.trim())
      .isEqualTo("shadow.example.Config.ConditionalOnClass=shadow.example.Class1")
  }

  @Test
  fun mergeAndRelocateSpringSchemas() {
    val path = "META-INF/spring.schemas"
    val relocator = SimpleRelocator("com.example", "shadow.example")
    transformer.transform(
      textContext(path, "http\\://www.example.com/schema/foo.xsd=com/example/foo.xsd", relocator)
    )
    transformer.transform(
      textContext(path, "http\\://www.example.com/schema/bar.xsd=com/example/bar.xsd", relocator)
    )

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val content = JarPath(tempJar).use { it.getContent(path) }
    val expected =
      "http\\://www.example.com/schema/bar.xsd=shadow/example/bar.xsd\n" +
        "http\\://www.example.com/schema/foo.xsd=shadow/example/foo.xsd"
    assertThat(content.trim()).isEqualTo(expected)
  }

  @Test
  fun mergeAndRelocateSpringTooling() {
    val path = "META-INF/spring.tooling"
    val relocator = SimpleRelocator("com.example", "shadow.example")
    transformer.transform(textContext(path, "com.example.Tool=com.example.ToolSupport", relocator))
    transformer.transform(textContext(path, "com.example.Tool=com.example.OtherSupport", relocator))

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val content = JarPath(tempJar).use { it.getContent(path) }
    assertThat(content.trim())
      .isEqualTo("shadow.example.Tool=shadow.example.ToolSupport,shadow.example.OtherSupport")
  }

  @Test
  fun mergeAndRelocateAotFactories() {
    val path = "META-INF/spring/aot.factories"
    val relocator = SimpleRelocator("com.example", "shadow.example")
    transformer.transform(textContext(path, "com.example.Factory=com.example.Impl1", relocator))
    transformer.transform(textContext(path, "com.example.Factory=com.example.Impl2", relocator))

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val content = JarPath(tempJar).use { it.getContent(path) }
    assertThat(content.trim())
      .isEqualTo("shadow.example.Factory=shadow.example.Impl1,shadow.example.Impl2")
  }

  private companion object {
    @JvmStatic
    fun resourceProvider() =
      listOf(
        // path, expected
        Arguments.of("META-INF/spring.factories", true),
        Arguments.of("META-INF/spring-autoconfigure-metadata.properties", true),
        Arguments.of("META-INF/spring.handlers", true),
        Arguments.of("META-INF/spring.schemas", true),
        Arguments.of("META-INF/spring.tooling", true),
        Arguments.of("META-INF/spring/aot.factories", true),
        Arguments.of(
          "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
          true,
        ),
        Arguments.of("META-INF/spring/another.imports", true),
        Arguments.of("META-INF/spring.xml", false),
        Arguments.of("foo/bar.properties", false),
      )
  }
}
