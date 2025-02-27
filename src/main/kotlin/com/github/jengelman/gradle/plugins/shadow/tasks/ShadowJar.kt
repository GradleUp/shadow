package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker
import com.github.jengelman.gradle.plugins.shadow.internal.fileCollection
import com.github.jengelman.gradle.plugins.shadow.internal.multiReleaseAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.setProperty
import com.github.jengelman.gradle.plugins.shadow.internal.sourceSets
import com.github.jengelman.gradle.plugins.shadow.relocation.CacheableRelocator
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.CacheableTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer.Companion.create
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import java.io.File
import java.io.IOException
import java.util.jar.JarFile
import kotlin.reflect.full.hasAnnotation
import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.UncheckedIOException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression

@CacheableTask
public abstract class ShadowJar :
  Jar(),
  ShadowSpec {
  private val dependencyFilterForMinimize = MinimizeDependencyFilter(project)

  private val includedZipTrees = project.provider {
    includedDependencies.files.map { project.zipTree(it) }
  }

  init {
    // https://github.com/gradle/gradle/blob/df5bc230c57db70aa3f6909403e5f89d7efde531/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/copy/DuplicateHandlingCopyActionDecorator.java#L55-L64
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest = DefaultInheritManifest(services.get(FileResolver::class.java))

    outputs.doNotCacheIf("Has one or more transforms or relocators that are not cacheable") {
      transformers.get().any { !it::class.hasAnnotation<CacheableTransformer>() } ||
        relocators.get().any { !it::class.hasAnnotation<CacheableRelocator>() }
    }
  }

  /**
   * Minimize the jar by removing unused classes.
   *
   * Defaults to `false`.
   */
  @get:Input
  public open val minimizeJar: Property<Boolean> = objectFactory.property(false)

  @get:Classpath
  public open val toMinimize: ConfigurableFileCollection = objectFactory.fileCollection {
    minimizeJar.map {
      if (it) (dependencyFilterForMinimize.resolve(configurations.get()) - apiJars) else emptySet()
    }
  }

  @get:Classpath
  public open val apiJars: ConfigurableFileCollection = objectFactory.fileCollection {
    minimizeJar.map {
      if (it) UnusedTracker.getApiJarsFromProject(project) else emptySet()
    }
  }

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public open val sourceSetsClassesDirs: ConfigurableFileCollection = objectFactory.fileCollection {
    minimizeJar.map {
      if (it) {
        project.sourceSets.map { sourceSet -> sourceSet.output.classesDirs.filter(File::isDirectory) }
      } else {
        emptySet()
      }
    }
  }

  @get:Nested
  public open val transformers: SetProperty<ResourceTransformer> = objectFactory.setProperty()

  @get:Nested
  public open val relocators: SetProperty<Relocator> = objectFactory.setProperty()

  @get:Classpath
  @get:Optional
  public open val configurations: SetProperty<Configuration> = objectFactory.setProperty()

  @get:Input
  public open val dependencyFilter: Property<DependencyFilter> =
    objectFactory.property(DefaultDependencyFilter(project))

  @get:Classpath
  public open val includedDependencies: ConfigurableFileCollection = objectFactory.fileCollection {
    dependencyFilter.zip(configurations) { df, cs -> df.resolve(cs) }
  }

  /**
   * Enable relocation of packages in the jar.
   *
   * Defaults to `false`.
   */
  @get:Input
  public open val enableRelocation: Property<Boolean> = objectFactory.property(false)

  /**
   * Prefix to use for relocated packages.
   *
   * Defaults to [ShadowBasePlugin.SHADOW].
   */
  @get:Input
  public open val relocationPrefix: Property<String> = objectFactory.property(ShadowBasePlugin.SHADOW)

  @Internal
  override fun getManifest(): InheritManifest = super.getManifest() as InheritManifest

  @Input // Trigger task executions after includes changed.
  override fun getIncludes(): MutableSet<String> = super.getIncludes()

  @Input // Trigger task executions after excludes changed.
  override fun getExcludes(): MutableSet<String> = super.getExcludes()

  override fun minimize(): ShadowJar = apply {
    minimizeJar.set(true)
  }

  override fun minimize(action: Action<DependencyFilter>?): ShadowJar = apply {
    minimize()
    action?.execute(dependencyFilterForMinimize)
  }

  override fun dependencies(action: Action<DependencyFilter>?): ShadowJar = apply {
    action?.execute(dependencyFilter.get())
  }

  override fun transform(clazz: Class<ResourceTransformer>): ShadowJar {
    return transform(clazz, null)
  }

  override fun <T : ResourceTransformer> transform(clazz: Class<T>, action: Action<T>?): ShadowJar = apply {
    addTransform(clazz.create(objectFactory), action)
  }

  override fun transform(transformer: ResourceTransformer): ShadowJar = apply {
    addTransform(transformer, null)
  }

  override fun mergeServiceFiles(): ShadowJar {
    return runCatching {
      transform(ServiceFileTransformer::class.java, null)
    }.getOrDefault(this)
  }

  override fun mergeServiceFiles(rootPath: String): ShadowJar {
    return runCatching {
      transform(ServiceFileTransformer::class.java) {
        it.path = rootPath
      }
    }.getOrDefault(this)
  }

  override fun mergeServiceFiles(action: Action<ServiceFileTransformer>?): ShadowJar {
    return runCatching {
      transform(ServiceFileTransformer::class.java, action)
    }.getOrDefault(this)
  }

  override fun mergeGroovyExtensionModules(): ShadowJar {
    return runCatching {
      transform(GroovyExtensionModuleTransformer::class.java, null)
    }.getOrDefault(this)
  }

  override fun append(resourcePath: String): ShadowJar {
    return append(resourcePath, AppendingTransformer.DEFAULT_SEPARATOR)
  }

  /**
   * Append contents to a resource in the jar.
   *
   * e.g. `append("resources/application.yml", "\n---\n")` for merging `resources/application.yml` files.
   *
   * @param resourcePath The path to the resource in the jar.
   * @param separator The separator to use between the original content and the appended content,
   * defaults to `\n` ([AppendingTransformer.DEFAULT_SEPARATOR]).
   */
  override fun append(resourcePath: String, separator: String): ShadowJar {
    return runCatching {
      transform(AppendingTransformer::class.java) {
        it.resource.set(resourcePath)
        it.separator.set(separator)
      }
    }.getOrDefault(this)
  }

  override fun relocate(pattern: String, destination: String): ShadowJar {
    return relocate(pattern, destination, null)
  }

  override fun relocate(
    pattern: String,
    destination: String,
    action: Action<SimpleRelocator>?,
  ): ShadowJar = apply {
    val relocator = SimpleRelocator(pattern, destination)
    addRelocator(relocator, action)
  }

  override fun relocate(relocator: Relocator): ShadowJar = apply {
    addRelocator(relocator, null)
  }

  override fun relocate(clazz: Class<Relocator>): ShadowJar {
    return relocate(clazz, null)
  }

  override fun <R : Relocator> relocate(clazz: Class<R>, action: Action<R>?): ShadowJar = apply {
    val relocator = clazz.getDeclaredConstructor().newInstance()
    addRelocator(relocator, action)
  }

  @TaskAction
  override fun copy() {
    from(includedZipTrees.get())
    injectMultiReleaseAttrIfPresent()
    super.copy()
  }

  override fun createCopyAction(): CopyAction {
    val zosProvider = { destination: File ->
      try {
        val entryCompressionMethod = when (entryCompression) {
          ZipEntryCompression.DEFLATED -> ZipOutputStream.DEFLATED
          ZipEntryCompression.STORED -> ZipOutputStream.STORED
          else -> throw IllegalArgumentException("Unknown Compression type $entryCompression.")
        }
        ZipOutputStream(destination).apply {
          setUseZip64(if (isZip64) Zip64Mode.AsNeeded else Zip64Mode.Never)
          setMethod(entryCompressionMethod)
        }
      } catch (e: Exception) {
        throw UncheckedIOException("Unable to create ZIP output stream for file $destination.", e)
      }
    }
    val unusedClasses = if (minimizeJar.get()) {
      val unusedTracker = UnusedTracker.forProject(apiJars, sourceSetsClassesDirs.files, toMinimize)
      includedDependencies.files.forEach {
        unusedTracker.addDependency(it)
      }
      unusedTracker.findUnused()
    } else {
      emptySet()
    }
    return ShadowCopyAction(
      archiveFile.get().asFile,
      zosProvider,
      transformers.get(),
      relocators.get() + packageRelocators,
      unusedClasses,
      isPreserveFileTimestamps,
      metadataCharset,
    )
  }

  private fun <R : Relocator> addRelocator(relocator: R, action: Action<R>?) {
    action?.execute(relocator)
    relocators.add(relocator)
  }

  private fun <T : ResourceTransformer> addTransform(transformer: T, action: Action<T>?) {
    action?.execute(transformer)
    transformers.add(transformer)
  }

  private val packageRelocators: List<SimpleRelocator>
    get() {
      if (!enableRelocation.get()) return emptyList()
      val prefix = relocationPrefix.get()
      return includedDependencies.files.flatMap { file ->
        JarFile(file).use { jarFile ->
          jarFile.entries().toList()
            .filter { it.name.endsWith(".class") && it.name != "module-info.class" }
            .map { it.name.substringBeforeLast('/').replace('/', '.') }
            .toSet()
            .map { SimpleRelocator(it, "$prefix.$it") }
        }
      }
    }

  private fun injectMultiReleaseAttrIfPresent() {
    val includeMultiReleaseAttr = includedDependencies.files.any {
      try {
        JarFile(it).use { jarFile ->
          // Manifest might be null or the attribute name is invalid, or any other case.
          runCatching { jarFile.manifest.mainAttributes.getValue(multiReleaseAttributeKey) }.getOrNull()
        } == "true"
      } catch (_: IOException) {
        // If the jar file is not valid, ignore it.
        false
      }
    }
    if (includeMultiReleaseAttr) {
      manifest.attributes[multiReleaseAttributeKey] = true
    }
  }
}
