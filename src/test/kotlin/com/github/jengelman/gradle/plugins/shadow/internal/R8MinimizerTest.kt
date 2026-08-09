package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.nio.file.Path
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.ZipFile
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.process.ExecOperations
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class R8MinimizerTest {
  private val project = ProjectBuilder.builder().build()

  @Test
  fun normalizeJarPreservesUnixPermissions(@TempDir tempDir: Path) {
    val inputJar = tempDir.resolve("input.jar")
    val outputJar = tempDir.resolve("output.jar")
    val expectedExecutableMode = UnixStat.FILE_FLAG or 493 // 0755 octal

    inputJar.zipOutputStream().use { zos ->
      zos.writeEntry("bin/script.sh", unixMode = UnixMode.raw(expectedExecutableMode)) {
        write("echo hello\n".toByteArray())
      }
      zos.writeEntry("com/example/Foo.class") {
        write("class bytes".toByteArray())
      }
      zos.writeEntry("META-INF/MANIFEST.MF") {
        write("Manifest-Version: 1.0\n".toByteArray())
      }
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
        metadataCharset = Charsets.UTF_8.toString(),
      )

    minimizer.normalizeJar(inputJar.toFile(), outputJar.toFile())

    ZipFile(outputJar.toFile()).use { zipFile ->
      // Executable unix mode must be preserved
      val scriptEntry = zipFile.getEntry("bin/script.sh")
      assertThat(scriptEntry.unixMode).isEqualTo(expectedExecutableMode)
    }
  }
}
