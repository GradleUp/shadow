package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import java.io.File
import java.util.jar.JarFile
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
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
 * Shadow-generated rules are based on the final jar contents. Source-set classes are kept as roots,
 * dependencies excluded from minimization are kept, and service descriptors keep providers for
 * downstream `ServiceLoader` users. User rule files and inline rules are appended after these rules
 * within Shadow's generated configuration. R8 applies consumer rules embedded in the jar as
 * separate configuration sources, with no ordering guarantee relative to Shadow's configuration.
 *
 * The default R8 configuration is shrink-only. Shadow passes `--no-minification` to disable name
 * obfuscation and generates `-dontoptimize` unless optimization is enabled explicitly.
 */
internal fun minimizeWithR8(
  inputJar: File,
  outputJar: File,
  execOperations: ExecOperations,
  logger: Logger,
  r8Classpath: FileCollection,
  r8Spec: DefaultR8Spec,
  javaLauncher: Provider<JavaLauncher>,
  sourceSetsClassesDirs: Iterable<File>,
  keptDependencyFiles: Iterable<File>,
  relocators: Iterable<Relocator>,
) {
  if (r8Classpath.isEmpty) {
    throw GradleException(
      "R8 minimization requires a non-empty R8 classpath. Apply the Shadow plugin or configure the shadowR8 configuration."
    )
  }

  val r8Dir = outputJar.parentFile.also { it.mkdirs() }
  val rulesFile = r8Dir.resolve("rules.pro")
  val configurationFile = r8Spec.configurationFile.get().asFile
  val launcher = javaLauncher.orNull
  val javaHome =
    launcher?.metadata?.installationPath?.asFile?.absolutePath ?: System.getProperty("java.home")
  if (javaHome.isNullOrBlank()) {
    throw GradleException("R8 minimization requires the java.home system property.")
  }

  val r8Args = r8Spec.args.get()
  rulesFile.writeText(
    createRules(
        baseDirectory = configurationFile.parentFile.apply { mkdirs() },
        inputJar = inputJar,
        r8Args = r8Args,
        r8Spec = r8Spec,
        sourceSetsClassesDirs = sourceSetsClassesDirs,
        keptDependencyFiles = keptDependencyFiles,
        relocators = relocators,
      )
      .joinToString(System.lineSeparator())
  )

  val arguments = buildList {
    add("--classfile")
    add("--output")
    add(outputJar.absolutePath)
    add("--pg-conf")
    add(rulesFile.absolutePath)
    add("--pg-conf-output")
    add(configurationFile.absolutePath)
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
}

private fun createRules(
  baseDirectory: File,
  inputJar: File,
  r8Args: List<String>,
  r8Spec: DefaultR8Spec,
  sourceSetsClassesDirs: Iterable<File>,
  keptDependencyFiles: Iterable<File>,
  relocators: Iterable<Relocator>,
): List<String> {
  return buildList {
    add(baseDirectory.toBaseDirectoryRule())
    if (shouldDisableOptimization(r8Spec, r8Args)) {
      add(DefaultR8Spec.DONT_OPTIMIZE_RULE)
    }
    addAll(sourceProguardRules(inputJar, sourceSetsClassesDirs, relocators))
    addAll(keptDependencyRules(inputJar, keptDependencyFiles, relocators))
    addAll(serviceProguardRules(inputJar))
    r8Spec.proguardRuleFiles
      .sortedBy { it.absolutePath }
      .forEach { file ->
        if (file.isFile) {
          addAll(file.readLines())
        }
      }
    addAll(r8Spec.proguardRules.get())
  }
}

private fun shouldDisableOptimization(r8Spec: DefaultR8Spec, r8Args: List<String>): Boolean {
  return !r8Spec.optimizationEnabled.get() &&
    (r8Spec.obfuscationEnabled.get() || DefaultR8Spec.NO_MINIFICATION_ARG in r8Args)
}

// Project classes are the public surface of the shadowed jar, even when nothing in the input jar
// refers to every class directly.
private fun sourceProguardRules(
  inputJar: File,
  sourceSetsClassesDirs: Iterable<File>,
  relocators: Iterable<Relocator>,
): List<String> {
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
private fun keptDependencyRules(
  inputJar: File,
  keptDependencyFiles: Iterable<File>,
  relocators: Iterable<Relocator>,
): List<String> {
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
private fun serviceProguardRules(inputJar: File): List<String> {
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

private fun File.toBaseDirectoryRule(): String {
  // Preserve Windows separators: escaping backslashes changes the paths R8 writes to the
  // collective configuration produced through --pg-conf-output.
  val normalizedPath = absolutePath.replace("'", "\\'")
  return "-basedirectory '$normalizedPath'"
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

private fun String.isJavaTypeName(): Boolean = javaTypeNameRegex.matches(this)

// R8 writes a fresh jar, so rewrite it through Shadow's archive settings to preserve
// reproducible ordering, timestamps, compression, zip64, and metadata charset behavior.
internal fun normalizeJar(
  inputJar: File,
  outputJar: File,
  preserveFileTimestamps: Boolean,
  reproducibleFileOrder: Boolean,
  zosProvider: (destination: File) -> ZipOutputStream,
) {
  // Use org.apache.tools.zip.ZipFile instead of java.util.jar.JarFile to access entry.unixMode
  // permissions and ensure uniform Zip structure handling.
  ZipFile(inputJar).use { zipFile ->
    val entries =
      zipFile.entries
        .asSequence()
        .filter { !it.isDirectory }
        .map { entry ->
          R8JarEntry(
            name = entry.name,
            time = entry.time,
            unixMode = entry.unixMode,
          )
        }
        .toList()

    val orderedEntries = if (reproducibleFileOrder) entries.sortedBy { it.name } else entries

    zosProvider(outputJar).use { zos ->
      val added = mutableSetOf<String>()

      orderedEntries.forEach { entry ->
        entry.name.parentDirectoryEntries().forEach { entryName ->
          if (!added.add(entryName)) return@forEach
          zos.writeEntry(
            name = entryName,
            preserveLastModified = preserveFileTimestamps,
            unixMode = UnixMode.directory(),
          )
        }
        if (added.add(entry.name)) {
          val zipEntry = zipFile.getEntry(entry.name)
          val unixMode = if (entry.unixMode != 0) UnixMode.raw(entry.unixMode) else UnixMode.file()
          zos.writeEntry(
            name = entry.name,
            preserveLastModified = preserveFileTimestamps,
            lastModified = entry.time,
            unixMode = unixMode,
          ) {
            zipFile.getInputStream(zipEntry).use { input ->
              input.copyTo(this)
            }
          }
        }
      }
    }
  }
}

private const val R8_MAIN_CLASS = "com.android.tools.r8.R8"
private const val SERVICES_PATH = "META-INF/services/"
// Keep only ordinary dot-separated Java type names in generated rules. This filters out blank
// service lines, comments, malformed providers, and JVM-only names R8 would reject.
private val javaTypeNameRegex = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")

private data class R8JarEntry(val name: String, val time: Long, val unixMode: Int)
