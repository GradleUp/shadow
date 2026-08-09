package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.process.ExecOperations
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class R8MinimizerTest {
  private val project = ProjectBuilder.builder().build()

  @Test
  fun normalizeJarPreservesUnixPermissions(@TempDir tempDir: File) {
    val inputJar = tempDir.resolve("input.jar")
    val outputJar = tempDir.resolve("output.jar")
    val expectedExecutableMode = UnixStat.FILE_FLAG or 493 // 0755 octal

    ZipOutputStream(inputJar.outputStream()).use { zos ->
      val scriptEntry =
        ZipEntry("bin/script.sh").apply {
          unixMode = expectedExecutableMode
        }
      zos.putNextEntry(scriptEntry)
      zos.write("echo hello\n".toByteArray())
      zos.closeEntry()

      val classEntry = ZipEntry("com/example/Foo.class")
      zos.putNextEntry(classEntry)
      zos.write("class bytes".toByteArray())
      zos.closeEntry()

      val manifestEntry = ZipEntry("META-INF/MANIFEST.MF")
      zos.putNextEntry(manifestEntry)
      zos.write("Manifest-Version: 1.0\n".toByteArray())
      zos.closeEntry()
    }

    val execOperations = (project as ProjectInternal).services.get(ExecOperations::class.java)
    val r8Spec = project.objects.newInstance(DefaultR8Spec::class.java)
    val minimizer =
      R8Minimizer(
        execOperations = execOperations,
        logger = project.logger,
        r8Classpath = project.files(),
        r8Spec = r8Spec,
        javaLauncher = project.provider { null },
        sourceSetsClassesDirs = emptyList(),
        keptDependencyFiles = emptyList(),
        relocators = emptyList(),
        preserveFileTimestamps = true,
        reproducibleFileOrder = true,
        zip64 = false,
        entryCompression = ZipEntryCompression.DEFLATED,
        metadataCharset = "UTF-8",
      )

    minimizer.normalizeJar(inputJar, outputJar)

    ZipFile(outputJar).use { zipFile ->
      // Executable unix mode must be preserved
      val scriptEntry = zipFile.getEntry("bin/script.sh")
      assertThat(scriptEntry.unixMode).isEqualTo(expectedExecutableMode)
    }
  }
}
