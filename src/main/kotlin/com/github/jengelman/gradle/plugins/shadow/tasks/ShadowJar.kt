package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.unsafeLazy
import com.github.jengelman.gradle.plugins.shadow.relocation.CacheableRelocator
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.CacheableTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import java.util.jar.JarFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.util.PatternSet

@CacheableTask
public abstract class ShadowJar :
  Jar(),
  ShadowSpec {
  private val dependencyFilterForMinimize = MinimizeDependencyFilter(project)

  init {
    // shadow filters out files later. This was the default behavior in  Gradle < 6.x
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest = DefaultInheritManifest(services.get(FileResolver::class.java))

    outputs.doNotCacheIf("Has one or more transforms or relocators that are not cacheable") {
      transformers.get().any { !isCacheableTransform(it::class.java) } ||
        relocators.get().any { !isCacheableRelocator(it::class.java) }
    }
  }

  @get:Internal
  override val stats: ShadowStats = ShadowStats()

  @get:Classpath
  public val toMinimize: ConfigurableFileCollection by unsafeLazy {
    project.objects.fileCollection().apply {
      if (minimizeJar.get()) {
        setFrom(dependencyFilterForMinimize.resolve(configurations.get()) - apiJars)
      }
    }
  }

  @get:Classpath
  public val apiJars: ConfigurableFileCollection by unsafeLazy {
    project.objects.fileCollection().apply {
      if (minimizeJar.get()) {
        setFrom(UnusedTracker.getApiJarsFromProject(project))
      }
    }
  }

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public val sourceSetsClassesDirs: ConfigurableFileCollection by unsafeLazy {
    project.objects.fileCollection().apply {
      if (minimizeJar.get()) {
        project.extensions.getByType(SourceSetContainer::class.java).forEach { sourceSet ->
          from(sourceSet.output.classesDirs.filter { it.isDirectory })
        }
      }
    }
  }

  @get:Classpath
  public abstract val includedDependencies: ConfigurableFileCollection

  @get:Internal
  public val rootPatternSet: PatternSet
    get() = (mainSpec.buildRootResolver() as DefaultCopySpec.DefaultCopySpecResolver).patternSet

  @get:Internal
  internal val internalCompressor: ZipCompressor
    get() {
      return when (entryCompression) {
        ZipEntryCompression.DEFLATED -> DefaultZipCompressor(isZip64, ZipOutputStream.DEFLATED)
        ZipEntryCompression.STORED -> DefaultZipCompressor(isZip64, ZipOutputStream.STORED)
        else -> throw IllegalArgumentException("Unknown Compression type $entryCompression")
      }
    }

  @get:Nested
  public abstract val transformers: ListProperty<Transformer>

  @get:Nested
  public abstract val relocators: ListProperty<Relocator>

  @get:Classpath
  @get:Optional
  public abstract val configurations: ListProperty<Configuration>

  @get:Internal
  public abstract val dependencyFilter: Property<DependencyFilter>

  @get:Input
  public abstract val enableRelocation: Property<Boolean>

  @get:Input
  public abstract val relocationPrefix: Property<String>

  @get:Input
  public abstract val minimizeJar: Property<Boolean>

  @Internal
  override fun getManifest(): InheritManifest = super.manifest as InheritManifest

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

  override fun transform(clazz: Class<Transformer>): ShadowJar {
    return transform(clazz, null)
  }

  override fun <T : Transformer> transform(clazz: Class<T>, action: Action<T>?): ShadowJar = apply {
    // If the constructor takes a single ObjectFactory, inject it in.
    val constructor = clazz.constructors.find {
      it.parameterTypes.singleOrNull() == ObjectFactory::class.java
    }
    val transformer = if (constructor != null) {
      objectFactory.newInstance(clazz)
    } else {
      clazz.getDeclaredConstructor().newInstance()
    }
    addTransform(transformer, action)
  }

  override fun transform(transformer: Transformer): ShadowJar = apply {
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
        it.setPath(rootPath)
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
    return runCatching {
      transform(AppendingTransformer::class.java) {
        it.resource.set(resourcePath)
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
    from(includedDependencies)
    super.copy()
    logger.info(stats.toString())
  }

  override fun createCopyAction(): CopyAction {
    val documentationRegistry = services.get(DocumentationRegistry::class.java)
    val unusedTracker = if (minimizeJar.get()) {
      UnusedTracker.forProject(apiJars, sourceSetsClassesDirs.files, toMinimize)
    } else {
      null
    }
    return ShadowCopyAction(
      archiveFile.get().asFile,
      internalCompressor,
      documentationRegistry,
      metadataCharset,
      transformers.get(),
      relocators.get() + packageRelocators,
      rootPatternSet,
      stats,
      isPreserveFileTimestamps,
      minimizeJar.get(),
      unusedTracker,
    )
  }

  private fun <R : Relocator> addRelocator(relocator: R, action: Action<R>?) {
    action?.execute(relocator)
    relocators.add(relocator)
  }

  private fun <T : Transformer> addTransform(transformer: T, action: Action<T>?) {
    action?.execute(transformer)
    transformers.add(transformer)
  }

  private fun isCacheableRelocator(clazz: Class<out Relocator>): Boolean {
    return clazz.isAnnotationPresent(CacheableRelocator::class.java)
  }

  private fun isCacheableTransform(clazz: Class<out Transformer>): Boolean {
    return clazz.isAnnotationPresent(CacheableTransformer::class.java)
  }

  private val packageRelocators: List<SimpleRelocator>
    get() {
      if (!enableRelocation.get()) return emptyList()

      val prefix = relocationPrefix.get()
      // Must cast configurations to List<FileCollection> to fix type mismatch in runtime.
      return (configurations.get() as List<FileCollection>).flatMap { configuration ->
        configuration.files.flatMap { file ->
          JarFile(file).use { jarFile ->
            jarFile.entries().toList()
              .filter { it.name.endsWith(".class") && it.name != "module-info.class" }
              .map { it.name.substringBeforeLast('/').replace('/', '.') }
              .map { SimpleRelocator(it, "$prefix.$it") }
          }
        }
      }
    }
}
