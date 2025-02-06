package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.util.SimpleRelocator
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.apache.commons.io.IOUtils
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServiceResourceTransformerTest {
  private lateinit var tempJar: File

  @BeforeEach
  fun setup() {
    tempJar = File.createTempFile("shade.", ".jar")
  }

  @AfterEach
  fun cleanup() {
    tempJar.delete()
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

    FileOutputStream(tempJar).use { fos ->
      ZipOutputStream(fos).use { zos ->
        transformer.modifyOutputStream(zos, false)
      }
    }

    val jarFile = java.util.zip.ZipFile(tempJar)
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

    FileOutputStream(tempJar).use { fos ->
      ZipOutputStream(fos).use { zos ->
        transformer.modifyOutputStream(zos, false)
      }
    }

    val jarFile = java.util.zip.ZipFile(tempJar)
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

    FileOutputStream(tempJar).use { fos ->
      ZipOutputStream(fos).use { zos ->
        transformer.modifyOutputStream(zos, false)
      }
    }

    val jarFile = java.util.zip.ZipFile(tempJar)
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

    FileOutputStream(tempJar).use { fos ->
      ZipOutputStream(fos).use { zos ->
        transformer.modifyOutputStream(zos, false)
      }
    }

    val jarFile = java.util.zip.ZipFile(tempJar)
    val jarEntry = jarFile.getEntry(contentResource)
    assertThat(jarEntry).isNotNull()
    jarFile.getInputStream(jarEntry).use { entryStream ->
      val xformedContent = IOUtils.toString(entryStream, StandardCharsets.UTF_8)
      val classes = xformedContent.split("\n")
      assertThat(classes.contains("org.blah.Service")).isTrue()
      assertThat(classes.contains("borg.foo.Service")).isTrue()
    }
  }

  private fun context(path: String, inputStream: InputStream, relocators: Set<Relocator>): TransformerContext {
    return TransformerContext(path, inputStream, relocators)
  }
}
