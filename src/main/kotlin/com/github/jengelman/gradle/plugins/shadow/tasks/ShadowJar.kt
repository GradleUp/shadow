package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultInheritManifest
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
import javax.inject.Inject
import kotlin.reflect.full.hasAnnotation
import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ArchiveOperations
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
import org.gradle.api.tasks.options.Option

@CacheableTask
public abstract class ShadowJar :
  Jar(),
  ShadowSpec {
  private val dependencyFilterForMinimize = MinimizeDependencyFilter(project)

  init {
    // https://github.com/gradle/gradle/blob/df5bc230c57db70aa3f6909403e5f89d7efde531/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/copy/DuplicateHandlingCopyActionDecorator.java#L55-L64
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest = DefaultInheritManifest(services.get(FileResolver::class.java))

    outputs.doNotCacheIf("Has one or more transforms or relocators that are not cacheable") {
      transformers.get().any { !it::class.hasAnnotation<CacheableTransformer>() } ||
        relocators.get().any { !it::class.hasAnnotation<CacheableRelocator>() }
    }
  }

  /**
   * Minimizes the jar by removing unused classes.
   *
   * Defaults to `false`.
   */
  @get:Input
  @get:Option(option = "minimize-jar", description = "Minimizes the jar by removing unused classes.")
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

  /**
   * [ResourceTransformer]s to be applied in the shadow steps.
   */
  @get:Nested
  public open val transformers: SetProperty<ResourceTransformer> = objectFactory.setProperty()

  /**
   * [Relocator]s to be applied in the shadow steps.
   */
  @get:Nested
  public open val relocators: SetProperty<Relocator> = objectFactory.setProperty()

  /**
   * The configurations to include dependencies from.
   *
   * Defaults to a set that contains `runtimeClasspath` or `runtime` configuration.
   */
  @get:Classpath
  @get:Optional
  public open val configurations: SetProperty<Configuration> = objectFactory.setProperty()

  @get:Input
  public open val dependencyFilter: Property<DependencyFilter> =
    objectFactory.property(DefaultDependencyFilter(project))

  /**
   * Final dependencies to be shadowed.
   */
  @get:Classpath
  public open val includedDependencies: ConfigurableFileCollection = objectFactory.fileCollection {
    dependencyFilter.zip(configurations) { df, cs -> df.resolve(cs) }
  }

  /**
   * Enables auto relocation of packages in the dependencies.
   *
   * Defaults to `false`.
   *
   * @see relocationPrefix
   */
  @get:Input
  @get:Option(option = "enable-auto-relocation", description = "Enables auto relocation of packages in the dependencies.")
  public open val enableAutoRelocation: Property<Boolean> = objectFactory.property(false)

  /**
   * Prefix used for auto relocation of packages in the dependencies.
   *
   * Defaults to `shadow`.
   *
   * @see enableAutoRelocation
   */
  @get:Input
  @get:Option(option = "relocation-prefix", description = "Prefix used for auto relocation of packages in the dependencies.")
  public open val relocationPrefix: Property<String> = objectFactory.property(ShadowBasePlugin.SHADOW)

  @Internal
  override fun getManifest(): InheritManifest = super.getManifest() as InheritManifest

  @Input // Trigger task executions after includes changed.
  override fun getIncludes(): MutableSet<String> = super.getIncludes()

  @Input // Trigger task executions after excludes changed.
  override fun getExcludes(): MutableSet<String> = super.getExcludes()

  @get:Inject
  protected abstract val archiveOperations: ArchiveOperations

  /**
   * Enable [minimizeJar], this equals to `minimizeJar.set(true)`.
   */
  override fun minimize() {
    minimizeJar.set(true)
  }

  /**
   * Enable [minimizeJar] and execute the [action] with the [DependencyFilter] for minimize.
   */
  override fun minimize(action: Action<DependencyFilter>?) {
    minimize()
    action?.execute(dependencyFilterForMinimize)
  }

  /**
   * Extra dependency operations to be applied in the shadow steps.
   */
  override fun dependencies(action: Action<DependencyFilter>?) {
    action?.execute(dependencyFilter.get())
  }

  /**
   * Merge Java services files.
   */
  override fun mergeServiceFiles() {
    transform(ServiceFileTransformer::class.java, null)
  }

  /**
   * Merge Java services files with [rootPath].
   */
  override fun mergeServiceFiles(rootPath: String) {
    transform(ServiceFileTransformer::class.java) {
      it.path = rootPath
    }
  }

  /**
   * Merge Java services files with [action].
   */
  override fun mergeServiceFiles(action: Action<ServiceFileTransformer>?) {
    transform(ServiceFileTransformer::class.java, action)
  }

  /**
   * Merge Groovy extension modules (`META-INF/**/org.codehaus.groovy.runtime.ExtensionModule`).
   */
  override fun mergeGroovyExtensionModules() {
    transform(GroovyExtensionModuleTransformer::class.java, null)
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
  override fun append(resourcePath: String, separator: String) {
    transform(AppendingTransformer::class.java) {
      it.resource.set(resourcePath)
      it.separator.set(separator)
    }
  }

  /**
   * Relocate classes and resources matching [pattern] to [destination] using [SimpleRelocator].
   */
  override fun relocate(
    pattern: String,
    destination: String,
    action: Action<SimpleRelocator>?,
  ) {
    val relocator = SimpleRelocator(pattern, destination)
    addRelocator(relocator, action)
  }

  /**
   * Relocate classes and resources using a [Relocator].
   */
  override fun <R : Relocator> relocate(clazz: Class<R>, action: Action<R>?) {
    val relocator = clazz.getDeclaredConstructor().newInstance()
    addRelocator(relocator, action)
  }

  /**
   * Relocate classes and resources using a [Relocator].
   */
  override fun <R : Relocator> relocate(relocator: R, action: Action<R>?) {
    addRelocator(relocator, action)
  }

  /**
   * Relocate classes and resources using a [Relocator].
   *
   * This is a convenience method for [relocate] with a reified type parameter for Kotlin.
   */
  public inline fun <reified R : Relocator> relocate() {
    relocate(R::class.java, null)
  }

  /**
   * Relocate classes and resources using a [Relocator].
   *
   * This is a convenience method for [relocate] with a reified type parameter for Kotlin.
   */
  public inline fun <reified R : Relocator> relocate(action: Action<R>?) {
    relocate(R::class.java, action)
  }

  /**
   * Transform resources using a [ResourceTransformer].
   */
  override fun <T : ResourceTransformer> transform(clazz: Class<T>, action: Action<T>?) {
    addTransform(clazz.create(objectFactory), action)
  }

  /**
   * Transform resources using a [ResourceTransformer].
   */
  override fun <T : ResourceTransformer> transform(transformer: T, action: Action<T>?) {
    addTransform(transformer, action)
  }

  /**
   * Transform resources using a [ResourceTransformer].
   *
   * This is a convenience method for [transform] with a reified type parameter for Kotlin.
   */
  public inline fun <reified T : ResourceTransformer> transform() {
    transform(T::class.java, null)
  }

  /**
   * Transform resources using a [ResourceTransformer].
   *
   * This is a convenience method for [transform] with a reified type parameter for Kotlin.
   */
  public inline fun <reified T : ResourceTransformer> transform(action: Action<T>?) {
    transform(T::class.java, action)
  }

  @TaskAction
  override fun copy() {
    from(
      includedDependencies.files.map { file ->
        if (file.extension.equals("aar", ignoreCase = true)) {
          val message = """
            Shadowing AAR file is not supported.
            Please exclude dependency artifact: $file
            or use Android Fused Library plugin instead. See https://developer.android.com/build/publish-library/fused-library.
          """.trimIndent()
          error(message)
        }
        archiveOperations.zipTree(file)
      },
    )
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
        throw IOException("Unable to create ZIP output stream for file $destination.", e)
      }
    }
    val unusedClasses = if (minimizeJar.get()) {
      val unusedTracker = UnusedTracker(
        sourceSetsClassesDirs = sourceSetsClassesDirs.files,
        classJars = apiJars,
        toMinimize = toMinimize,
      )
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
      if (!enableAutoRelocation.get()) return emptyList()
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
