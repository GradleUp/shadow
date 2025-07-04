package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class SpringFileTransformerTest : BaseTransformerTest<SpringFileTransformer>() {
  private lateinit var tempJar: Path

  @BeforeEach
  override fun setup() {
    super.setup()
    tempJar = createTempFile("spring-shade.", ".jar")
  }

  @AfterEach
  fun cleanup() {
    tempJar.deleteExisting()
  }

  @ParameterizedTest
  @MethodSource("springFileProvider")
  fun canTransformResource(path: String, expected: Boolean) {
    assertThat(transformer.canTransformResource(path)).isEqualTo(expected)
  }

  @Test
  fun hasTransformedResource() {
    val context = createContext("META-INF/spring.factories", "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.Config")
    transformer.transform(context)

    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun hasNotTransformedResource() {
    assertThat(transformer.hasTransformedResource()).isFalse()
  }

  @Test
  fun transformSpringFactoriesWithRelocation() {
    val relocator = SimpleRelocator("com.example", "com.relocated.example")
    val content = "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.Config,com.example.AnotherConfig"
    val context = createContext("META-INF/spring.factories", content, relocator)
    
    transformer.transform(context)

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = ZipFile(tempJar.toFile()).use { it.getContent("META-INF/spring.factories") }
    val expectedContent = "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.relocated.example.Config,com.relocated.example.AnotherConfig"
    assertThat(transformedContent).contains(expectedContent)
  }

  @Test
  fun transformSpringHandlersWithRelocation() {
    val relocator = SimpleRelocator("com.example", "com.relocated.example")
    val content = "http\\://example.com/schema=com.example.SchemaHandler"
    val context = createContext("META-INF/spring.handlers", content, relocator)
    
    transformer.transform(context)

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = ZipFile(tempJar.toFile()).use { it.getContent("META-INF/spring.handlers") }
    val expectedContent = "http\\://example.com/schema=com.relocated.example.SchemaHandler"
    assertThat(transformedContent).contains(expectedContent)
  }

  @Test
  fun transformSpringSchemasWithoutRelocation() {
    val relocator = SimpleRelocator("com.example", "com.relocated.example")
    val content = "http\\://example.com/schema=META-INF/schema.xsd"
    val context = createContext("META-INF/spring.schemas", content, relocator)
    
    transformer.transform(context)

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = ZipFile(tempJar.toFile()).use { it.getContent("META-INF/spring.schemas") }
    // Schema paths should not be relocated
    assertThat(transformedContent).contains("http\\://example.com/schema=META-INF/schema.xsd")
  }

  @Test
  fun mergeMultipleSpringFactoriesFiles() {
    val relocator = SimpleRelocator("com.example", "com.relocated.example")
    
    // First file
    val content1 = "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.Config1"
    val context1 = createContext("META-INF/spring.factories", content1, relocator)
    transformer.transform(context1)
    
    // Second file with same key
    val content2 = "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.Config2"
    val context2 = createContext("META-INF/spring.factories", content2, relocator)
    transformer.transform(context2)

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = ZipFile(tempJar.toFile()).use { it.getContent("META-INF/spring.factories") }
    // Should contain both relocated class names, merged with comma
    assertThat(transformedContent).contains("com.relocated.example.Config1,com.relocated.example.Config2")
  }

  @Test
  fun mergeMultipleSpringFactoriesFilesWithDifferentKeys() {
    val relocator = SimpleRelocator("com.example", "com.relocated.example")
    
    // First file
    val content1 = "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.Config1"
    val context1 = createContext("META-INF/spring.factories", content1, relocator)
    transformer.transform(context1)
    
    // Second file with different key
    val content2 = "org.springframework.context.ApplicationContextInitializer=com.example.Initializer"
    val context2 = createContext("META-INF/spring.factories", content2, relocator)
    transformer.transform(context2)

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = ZipFile(tempJar.toFile()).use { it.getContent("META-INF/spring.factories") }
    // Should contain both keys with relocated values
    assertThat(transformedContent).contains("org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.relocated.example.Config1")
    assertThat(transformedContent).contains("org.springframework.context.ApplicationContextInitializer=com.relocated.example.Initializer")
  }

  @Test
  fun transformSpringAutoconfigureMetadataWithRelocation() {
    val relocator = SimpleRelocator("com.example", "com.relocated.example")
    val content = "com.example.Config.ConditionalOnClass=com.example.Service"
    val context = createContext("META-INF/spring-autoconfigure-metadata.properties", content, relocator)
    
    transformer.transform(context)

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = ZipFile(tempJar.toFile()).use { it.getContent("META-INF/spring-autoconfigure-metadata.properties") }
    val expectedContent = "com.relocated.example.Config.ConditionalOnClass=com.relocated.example.Service"
    assertThat(transformedContent).contains(expectedContent)
  }

  @Test
  fun handleEmptyValues() {
    val relocator = SimpleRelocator("com.example", "com.relocated.example")
    val content = "key.with.empty.value="
    val context = createContext("META-INF/spring.factories", content, relocator)
    
    transformer.transform(context)

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = ZipFile(tempJar.toFile()).use { it.getContent("META-INF/spring.factories") }
    assertThat(transformedContent).contains("key.with.empty.value=")
  }

  @Test
  fun handleWhitespaceInCommaSeparatedValues() {
    val relocator = SimpleRelocator("com.example", "com.relocated.example")
    val content = "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.Config1, com.example.Config2 ,com.example.Config3"
    val context = createContext("META-INF/spring.factories", content, relocator)
    
    transformer.transform(context)

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = ZipFile(tempJar.toFile()).use { it.getContent("META-INF/spring.factories") }
    assertThat(transformedContent).contains("com.relocated.example.Config1,com.relocated.example.Config2,com.relocated.example.Config3")
  }

  private fun createContext(path: String, content: String, vararg relocators: Relocator): TransformerContext {
    return TransformerContext(path, content.byteInputStream(), relocators = relocators.toSet())
  }

  private fun ZipFile.getContent(entryName: String): String {
    val entry = requireNotNull(getEntry(entryName)) { "Entry $entryName not found" }
    return getInputStream(entry).bufferedReader().use { it.readText() }
  }

  private companion object {
    @JvmStatic
    fun springFileProvider() = listOf(
      Arguments.of("META-INF/spring.factories", true),
      Arguments.of("META-INF/spring.handlers", true),
      Arguments.of("META-INF/spring.schemas", true),
      Arguments.of("META-INF/spring.tooling", true),
      Arguments.of("META-INF/spring-autoconfigure-metadata.properties", true),
      Arguments.of("META-INF/spring-configuration-metadata.json", false),
      Arguments.of("META-INF/services/javax.servlet.ServletContainerInitializer", false),
      Arguments.of("META-INF/MANIFEST.MF", false),
      Arguments.of("some/other/file.properties", false),
    )
  }
}