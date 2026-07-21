package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.jar.JarFile
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations

/**
 * Runs R8 as a final-archive shrinker.
 *
 * Shadow first writes the complete jar, including relocations, resource transformers, merged
 * service files, and duplicate handling. R8 then processes that exact artifact.
 *
 * R8 does not know about Shadow's reproducible archive settings, so its output is normalized before
 * replacing the original jar.
 *
 * Generated rules are based on the final jar contents. Source-set classes are kept as roots,
 * dependencies excluded from minimization are kept, service descriptors keep providers for
 * downstream `ServiceLoader` users, and R8 consumer rules are extracted from the jar. User rule
 * files and inline rules are appended last.
 *
 * The default R8 configuration is shrink-only. Shadow passes `--no-minification` to disable name
 * obfuscation and generates `-dontoptimize` unless optimization is enabled explicitly.
 */
internal class R8Minimizer(
  private val execOperations: ExecOperations,
  private val logger: Logger,
  private val r8Classpath: FileCollection,
  private val r8Spec: DefaultR8Spec,
  private val javaLauncher: Provider<JavaLauncher>,
  private val sourceSetsClassesDirs: Iterable<File>,
  private val keptDependencyFiles: Iterable<File>,
  private val relocators: Iterable<Relocator>,
  private val preserveFileTimestamps: Boolean,
  private val reproducibleFileOrder: Boolean,
  private val zip64: Boolean,
  private val entryCompression: ZipEntryCompression,
  private val metadataCharset: String?,
) {
  fun minimize(inputJar: File, temporaryDir: File) {
    if (r8Classpath.isEmpty) {
      throw GradleException(
        "R8 minimization requires a non-empty R8 classpath. Apply the Shadow plugin or configure the shadowR8 configuration."
      )
    }

    val r8Dir = temporaryDir.resolve("r8").also { it.mkdirs() }
    val extractedRulesFile = r8Dir.resolve("classpath-rules.pro")
    val rulesFile = r8Dir.resolve("rules.pro")
    val r8Output = r8Dir.resolve("output.jar")
    val normalizedOutput = r8Dir.resolve("normalized-output.jar")
    val launcher = javaLauncher.orNull
    val javaHome =
      launcher?.metadata?.installationPath?.asFile?.absolutePath ?: System.getProperty("java.home")
    if (javaHome.isNullOrBlank()) {
      throw GradleException("R8 minimization requires the java.home system property.")
    }

    extractClasspathRules(inputJar, extractedRulesFile, launcher)

    val r8Args = r8Spec.args.get()
    rulesFile.writeText(
      createRules(inputJar, r8Args, extractedRulesFile).joinToString(System.lineSeparator())
    )

    val arguments = buildList {
      add("--classfile")
      add("--output")
      add(r8Output.absolutePath)
      add("--pg-conf")
      add(rulesFile.absolutePath)
      add("--lib")
      add(javaHome)
      addAll(r8Args)
      add(inputJar.absolutePath)
    }

    logger.info("Running R8 to minimize {}.", inputJar)
    execOperations.javaexec {
      it.classpath = r8Classpath
      it.mainClass.set(R8_MAIN_CLASS)
      if (launcher != null) {
        it.executable = launcher.executablePath.asFile.absolutePath
      }
      it.args(arguments)
    }

    normalizeJar(r8Output, normalizedOutput)
    Files.move(normalizedOutput.toPath(), inputJar.toPath(), REPLACE_EXISTING)
  }

  // R8's command line does not automatically apply consumer rules carried inside program jars.
  // Extract them from the exact jar R8 will shrink, matching the classpath-rule handling used by
  // Android builds.
  private fun extractClasspathRules(
    inputJar: File,
    outputFile: File,
    launcher: JavaLauncher?,
  ) {
    val arguments = buildList {
      add("--rules-output")
      add(outputFile.absolutePath)
      add("--include-origin-comments")
      add(inputJar.absolutePath)
    }

    logger.info("Extracting R8 rules from {}.", inputJar)
    execOperations.javaexec {
      it.classpath = r8Classpath
      it.mainClass.set(R8_RULES_MAIN_CLASS)
      if (launcher != null) {
        it.executable = launcher.executablePath.asFile.absolutePath
      }
      it.args(arguments)
    }
  }

  private fun createRules(
    inputJar: File,
    r8Args: List<String>,
    extractedRulesFile: File,
  ): List<String> {
    return buildList {
      if (shouldDisableOptimization(r8Args)) {
        add(DefaultR8Spec.DONT_OPTIMIZE_RULE)
      }
      addAll(sourceKeepRules(inputJar))
      addAll(keptDependencyRules(inputJar))
      addAll(serviceKeepRules(inputJar))
      addAll(extractedRulesFile.readLines())
      r8Spec.keepRuleFiles.files
        .sortedBy { it.absolutePath }
        .forEach { file ->
          if (file.isFile) {
            addAll(file.readLines())
          }
        }
      addAll(r8Spec.keepRules.get())
    }
  }

  private fun shouldDisableOptimization(r8Args: List<String>): Boolean {
    return !r8Spec.optimizationEnabled.get() &&
      (r8Spec.obfuscationEnabled.get() || DefaultR8Spec.NO_MINIFICATION_ARG in r8Args)
  }

  // Project classes are the public surface of the shadowed jar, even when nothing in the input jar
  // refers to every class directly.
  private fun sourceKeepRules(inputJar: File): List<String> {
    val jarClasses = jarClassEntries(inputJar)
    return sourceSetsClassesDirs
      .asSequence()
      .filter(File::isDirectory)
      .flatMap { dir ->
        dir
          .walkTopDown()
          .filter { it.isFile && it.name.endsWith(".class") }
          .mapNotNull { file ->
            file.toClassName(relativeTo = dir)
          }
      }
      .map { relocators.relocateClass(it) }
      .filter { it.isJavaTypeName() }
      .filter { className -> "${className.replace('.', '/')}.class" in jarClasses }
      .distinct()
      .sorted()
      .map { "-keep,includedescriptorclasses class $it { *; }" }
      .toList()
  }

  // Keep dependencies users explicitly excluded from minimization, matching the existing
  // minimize { exclude(...) } contract for the default analyzer.
  private fun keptDependencyRules(inputJar: File): List<String> {
    val jarClasses = jarClassEntries(inputJar)
    return keptDependencyFiles
      .asSequence()
      .flatMap { it.classNames() }
      .map { relocators.relocateClass(it) }
      .filter { it.isJavaTypeName() }
      .filter { className -> "${className.replace('.', '/')}.class" in jarClasses }
      .distinct()
      .sorted()
      .map { "-keep class $it { *; }" }
      .toList()
  }

  // Service descriptors are usage edges for downstream ServiceLoader calls, so keep the service
  // interface and every listed provider even if R8 sees no direct references.
  private fun serviceKeepRules(inputJar: File): List<String> {
    val rules = linkedSetOf<String>()
    JarFile(inputJar).use { jarFile ->
      jarFile
        .entries()
        .asSequence()
        .filter { !it.isDirectory && it.name.startsWith(SERVICES_PATH) }
        .sortedBy { it.name }
        .forEach { entry ->
          val serviceClass = entry.name.removePrefix(SERVICES_PATH).replace('/', '.')
          if (serviceClass.isJavaTypeName()) {
            rules += "-keep class $serviceClass { *; }"
          }
          jarFile.getInputStream(entry).bufferedReader().useLines { lines ->
            lines
              .map { it.substringBefore('#').trim() }
              .filter { it.isNotEmpty() && it.isJavaTypeName() }
              .forEach { provider -> rules += "-keep class $provider { *; }" }
          }
        }
    }
    return rules.toList()
  }

  private fun jarClassEntries(inputJar: File): Set<String> {
    return JarFile(inputJar).use { jarFile ->
      jarFile
        .entries()
        .asSequence()
        .filter { !it.isDirectory && it.name.endsWith(".class") }
        .map { it.name }
        .toSet()
    }
  }

  private fun File.toClassName(relativeTo: File): String? {
    if (name == "module-info.class" || name == "package-info.class") return null
    return relativeTo
      .toPath()
      .relativize(toPath())
      .toString()
      .replace(File.separatorChar, '/')
      .removeSuffix(".class")
      .replace('/', '.')
  }

  private fun File.classNames(): Sequence<String> {
    return when {
      isDirectory ->
        walkTopDown()
          .filter { it.isFile && it.name.endsWith(".class") }
          .mapNotNull {
            it.toClassName(relativeTo = this)
          }
      isFile ->
        JarFile(this)
          .use { jarFile ->
            jarFile
              .entries()
              .asSequence()
              .filter { !it.isDirectory && it.name.endsWith(".class") }
              .mapNotNull { it.name.toClassName() }
              .toList()
          }
          .asSequence()
      else -> emptySequence()
    }
  }

  private fun String.toClassName(): String? {
    val name = substringAfterLast('/')
    if (name == "module-info.class" || name == "package-info.class") return null
    return removeSuffix(".class").replace('/', '.')
  }

  // R8 writes a fresh jar, so rewrite it through Shadow's archive settings to preserve
  // reproducible ordering, timestamps, compression, zip64, and metadata charset behavior.
  private fun normalizeJar(inputJar: File, outputJar: File) {
    val entries =
      JarFile(inputJar).use { jarFile ->
        jarFile
          .entries()
          .asSequence()
          .filter { !it.isDirectory }
          .map { entry ->
            R8JarEntry(
              name = entry.name,
              time = entry.time,
              bytes = jarFile.getInputStream(entry).use { it.readBytes() },
            )
          }
          .toList()
      }
    val orderedEntries = if (reproducibleFileOrder) entries.sortedBy { it.name } else entries
    val entryCompressionMethod =
      when (entryCompression) {
        ZipEntryCompression.DEFLATED -> ZipOutputStream.DEFLATED
        ZipEntryCompression.STORED -> ZipOutputStream.STORED
      }
    val zipOutputStream =
      if (entryCompressionMethod == ZipOutputStream.STORED) {
        ZipOutputStream(outputJar)
      } else {
        ZipOutputStream(outputJar.outputStream().buffered())
      }
    zipOutputStream.use { zos ->
      if (metadataCharset != null) {
        zos.setEncoding(metadataCharset)
      }
      zos.setUseZip64(if (zip64) Zip64Mode.AsNeeded else Zip64Mode.Never)
      zos.setMethod(entryCompressionMethod)
      val added = mutableSetOf<String>()

      fun addParentDirs(name: String) {
        val parent = name.substringBeforeLast('/', "")
        if (parent.isEmpty()) return
        addParentDirs(parent)
        val entryName = "$parent/"
        if (added.add(entryName)) {
          zos.putNextEntry(
            zipEntry(entryName, preserveFileTimestamps) {
              unixMode = UnixStat.DIR_FLAG or DEFAULT_DIR_MODE
            }
          )
          zos.closeEntry()
        }
      }

      orderedEntries.forEach { entry ->
        addParentDirs(entry.name)
        if (added.add(entry.name)) {
          zos.putNextEntry(
            zipEntry(entry.name, preserveFileTimestamps, entry.time) {
              unixMode = UnixStat.FILE_FLAG or DEFAULT_FILE_MODE
            }
          )
          zos.write(entry.bytes)
          zos.closeEntry()
        }
      }
    }
  }

  private fun String.isJavaTypeName(): Boolean = javaTypeNameRegex.matches(this)

  // Not a data class because of the bytearray
  private class R8JarEntry(val name: String, val time: Long, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as R8JarEntry

      if (time != other.time) return false
      if (name != other.name) return false
      if (!bytes.contentEquals(other.bytes)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = time.hashCode()
      result = 31 * result + name.hashCode()
      result = 31 * result + bytes.contentHashCode()
      return result
    }

    override fun toString(): String {
      return "R8JarEntry(name='$name', time=$time, bytes=${bytes.toHexString()})"
    }
  }

  private companion object {
    const val R8_MAIN_CLASS = "com.android.tools.r8.R8"
    const val R8_RULES_MAIN_CLASS = "com.android.tools.r8.ExtractR8Rules"
    const val SERVICES_PATH = "META-INF/services/"
    const val DEFAULT_DIR_MODE = 493 // 0755
    const val DEFAULT_FILE_MODE = 420 // 0644
    // Keep only ordinary dot-separated Java type names in generated rules. This filters out blank
    // service lines, comments, malformed providers, and JVM-only names R8 would reject.
    val javaTypeNameRegex = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
  }
}
