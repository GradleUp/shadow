package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import java.io.File
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipEntry
import kotlin.io.path.moveTo
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
  temporaryDir: File,
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

  val r8Dir = temporaryDir.resolve("r8").also { it.mkdirs() }
  val rulesFile = r8Dir.resolve("rules.pro")
  val configurationFile = r8Spec.configurationFile.get().asFile
  val r8Output = r8Dir.resolve("output.jar")
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
    add(r8Output.absolutePath)
    add("--pg-conf")
    add(rulesFile.absolutePath)
    add("--pg-conf-output")
    add(configurationFile.absolutePath)
    add("--lib")
    add(javaHome)
    r8Spec.classpath
      .filter { it.exists() }
      .forEach { file ->
        add("--classpath")
        add(file.absolutePath)
      }
    addAll(r8Args)
    add(inputJar.absolutePath)
  }

  logger.info("Running R8 to minimize {}.", inputJar)
  execOperations.javaexec {
    it.classpath = r8Classpath
    it.mainClass.set("com.android.tools.r8.R8")
    if (launcher != null) {
      it.executable = launcher.executablePath.asFile.absolutePath
    }
    it.args(arguments)
  }

  r8Output.toPath().moveTo(inputJar.toPath(), REPLACE_EXISTING)
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
    add("-basedirectory '${baseDirectory.escapedAbsPath}'")

    if (r8Spec.useDefaultRules.get()) {
      val shouldDisableOptimization =
        !r8Spec.optimizationEnabled.get() &&
          (r8Spec.obfuscationEnabled.get() || DefaultR8Spec.NO_MINIFICATION_ARG in r8Args)
      if (shouldDisableOptimization) {
        add(DefaultR8Spec.DONT_OPTIMIZE_RULE)
      }

      val (jarClasses, serviceRules) = inputJar.analyzeInputJar()
      addAll(
        // Project classes are the public surface of the shadowed jar, even when nothing in the
        // input jar refers to every class directly.
        sourceSetsClassesDirs.toKeepRules(jarClasses, relocators, "-keep,includedescriptorclasses")
      )
      addAll(
        // Keep dependencies users explicitly excluded from minimization, matching the existing
        // minimize { exclude(...) } contract for the default analyzer.
        keptDependencyFiles.toKeepRules(jarClasses, relocators, "-keep")
      )
      addAll(serviceRules)
    }

    r8Spec.proguardRuleFiles
      .filter { it.isFile }
      .sortedBy { it.absolutePath }
      .forEach { file -> addAll(file.readLines()) }
    addAll(r8Spec.proguardRules.get())
  }
}

private fun Iterable<File>.toKeepRules(
  jarClasses: Set<String>,
  relocators: Iterable<Relocator>,
  rulePrefix: String,
): List<String> {
  return asSequence()
    .flatMap { it.classNames() }
    .map { relocators.relocateClass(it) }
    .filter { className -> className in jarClasses }
    .filter { it.isJavaTypeName() }
    .toSortedSet()
    .map { "$rulePrefix class $it { *; }" }
}

// Extracts all class names and generates keep rules for service descriptors in a single pass.
// Service descriptors are usage edges for downstream ServiceLoader calls, so keep the service
// interface and every listed provider even if R8 sees no direct references.
private fun File.analyzeInputJar(): Pair<Set<String>, Set<String>> {
  val classes = mutableSetOf<String>()
  val serviceEntries = mutableListOf<ZipEntry>()
  val serviceRules = linkedSetOf<String>()

  useZip {
    entries().asSequence().forEach { entry ->
      val name = entry.name
      when {
        entry.isDirectory -> Unit
        name.endsWith(".class") -> {
          name.toClassName()?.let { classes += it }
        }
        name.startsWith(SERVICES_PATH) -> {
          serviceEntries += entry
        }
      }
    }

    serviceEntries
      .sortedBy { it.name }
      .forEach { entry ->
        val serviceClass = entry.name.removePrefix(SERVICES_PATH).replace('/', '.')
        if (serviceClass.isJavaTypeName()) {
          serviceRules += "-keep,allowrepackage class $serviceClass { *; }"
        }
        getInputStream(entry).bufferedReader().useLines { lines ->
          lines
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() && it.isJavaTypeName() }
            .forEach { provider -> serviceRules += "-keep,allowrepackage class $provider { *; }" }
        }
      }
  }

  return classes to serviceRules
}

private fun File.toClassName(base: File): String? {
  if (name == "module-info.class" || name == "package-info.class") return null
  return toRelativeString(base).removeSuffix(".class").replace(File.separatorChar, '.')
}

private fun File.classNames(): List<String> {
  return when {
    isDirectory ->
      walkTopDown()
        .filter { it.name.endsWith(".class") && it.isFile }
        .mapNotNull { it.toClassName(base = this) }
        .toList()
    isFile ->
      useZip {
        entries()
          .asSequence()
          .filter { it.name.endsWith(".class") }
          .mapNotNull { it.name.toClassName() }
          .toList()
      }
    else -> emptyList()
  }
}

private val File.escapedAbsPath: String
  get() = absolutePath.replace("'", "\\'")

private fun String.toClassName(): String? {
  if (startsWith("META-INF/")) return null
  val name = substringAfterLast('/')
  if (name == "module-info.class" || name == "package-info.class") return null
  return removeSuffix(".class").replace('/', '.')
}

private fun String.isJavaTypeName(): Boolean = javaTypeNameRegex.matches(this)

private const val SERVICES_PATH = "META-INF/services/"
// Keep only ordinary dot-separated Java type names in generated rules. This filters out blank
// service lines, comments, malformed providers, and JVM-only names R8 would reject.
private val javaTypeNameRegex = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
