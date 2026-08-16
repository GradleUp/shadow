@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.ZipFile
import org.gradle.api.tasks.bundling.ZipEntryCompression

val R8MinimizerTests by testSuite {
  runTests(::R8MinimizerTest)
}

private class R8MinimizerTest(val tempDir: Path = createTempDirectory()) {
  fun normalizeJarPreservesUnixPermissions() {
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

    normalizeJar(
      inputJar = inputJar.toFile(),
      outputJar = outputJar.toFile(),
      preserveFileTimestamps = true,
      reproducibleFileOrder = true,
      zosProvider = { destination ->
        destination.createZipOutputStream(
          entryCompression = ZipEntryCompression.STORED,
          isZip64 = false,
          encoding = null,
        )
      },
    )

    ZipFile(outputJar.toFile()).use { zipFile ->
      // Executable unix mode must be preserved
      val scriptEntry = zipFile.getEntry("bin/script.sh")
      assertThat(scriptEntry.unixMode).isEqualTo(expectedExecutableMode)
    }
  }
}
