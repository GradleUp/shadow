package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.DependencyFilter
import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultInheritManifest
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.ShadowSpec
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker.Companion.getApiJarsFromProject
import com.github.jengelman.gradle.plugins.shadow.internal.runOrThrow
import com.github.jengelman.gradle.plugins.shadow.relocation.CacheableRelocator
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.CacheableTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import java.util.concurrent.Callable
import java.util.jar.JarFile
import java.util.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.util.PatternSet
import org.jetbrains.annotations.NotNull

@CacheableTask
public abstract class ShadowJar :
  Jar(),
  ShadowSpec {
  private val _transformers = mutableListOf<Transformer>()
  private val _relocators = mutableListOf<Relocator>()
  private val _configurations = mutableListOf<FileCollection>()
  private var minimizeJar = false
  private val dependencyFilterForMinimize = MinimizeDependencyFilter(project)
  private var _toMinimize: FileCollection? = null
  private var _apiJars: FileCollection? = null
  private var _sourceSetsClassesDirs: FileCollection? = null

  init {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest = DefaultInheritManifest(services.get(FileResolver::class.java))

    inputs.property("minimize") { minimizeJar }
    outputs.doNotCacheIf("Has one or more transforms or relocators that are not cacheable") {
      _transformers.any { !isCacheableTransform(it::class.java) } ||
        _relocators.any { !isCacheableRelocator(it::class.java) }
    }
  }

  public var transformers: List<Transformer>
    @Nested get() = _transformers
    set(value) {
      _transformers.clear()
      _transformers.addAll(value)
    }

  public var relocators: List<Relocator>
    @Nested get() = _relocators
    set(value) {
      _relocators.clear()
      _relocators.addAll(value)
    }

  public var configurations: List<FileCollection>
    @Classpath get() = _configurations
    set(value) {
      _configurations.clear()
      _configurations.addAll(value)
    }

  @get:Input
  public var enableRelocation: Boolean = false

  @get:Input
  public var relocationPrefix: String = "shadow"

  @get:Internal
  public var dependencyFilter: DependencyFilter = DefaultDependencyFilter(project)

  @get:Internal
  override val stats: ShadowStats = ShadowStats()

  @get:Internal
  public val internalCompressor: ZipCompressor
    get() {
      return when (entryCompression) {
        ZipEntryCompression.DEFLATED -> DefaultZipCompressor(isZip64, ZipOutputStream.DEFLATED)
        ZipEntryCompression.STORED -> DefaultZipCompressor(isZip64, ZipOutputStream.STORED)
        else -> throw IllegalArgumentException("Unknown Compression type $entryCompression")
      }
    }

  @get:Classpath
  public val toMinimize: FileCollection
    get() {
      if (_toMinimize == null) {
        _toMinimize = if (minimizeJar) {
          dependencyFilterForMinimize.resolve(_configurations).minus(apiJars)
        } else {
          project.objects.fileCollection()
        }
      }
      return _toMinimize!!
    }

  @get:Classpath
  public val apiJars: FileCollection
    get() {
      if (_apiJars == null) {
        _apiJars = if (minimizeJar) {
          getApiJarsFromProject(project)
        } else {
          project.objects.fileCollection()
        }
      }
      return _apiJars!!
    }

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public val sourceSetsClassesDirs: FileCollection
    get() {
      if (_sourceSetsClassesDirs == null) {
        val allClassesDirs = project.objects.fileCollection()
        if (minimizeJar) {
          for (sourceSet in project.extensions.getByType(SourceSetContainer::class.java)) {
            val classesDirs = sourceSet.output.classesDirs
            allClassesDirs.from(classesDirs)
          }
        }
        _sourceSetsClassesDirs = allClassesDirs.filter { file -> file.isDirectory }
      }
      return _sourceSetsClassesDirs!!
    }

  @get:Classpath
  public val includedDependencies: ConfigurableFileCollection
    get() = project.files(
      Callable {
        dependencyFilter.resolve(_configurations)
      },
    )

  @get:Internal
  public val rootPatternSet: PatternSet
    get() {
      return (mainSpec.buildRootResolver() as DefaultCopySpec.DefaultCopySpecResolver).patternSet
    }

  override fun minimize(): ShadowJar = apply {
    minimizeJar = true
  }

  override fun minimize(action: Action<DependencyFilter>?): ShadowJar = apply {
    minimize()
    action?.execute(dependencyFilterForMinimize)
  }

  override fun getManifest(): InheritManifest {
    return super.getManifest() as InheritManifest
  }

  @NotNull
  override fun createCopyAction(): CopyAction {
    val documentationRegistry = services.get(DocumentationRegistry::class.java)
    val unusedTracker = if (minimizeJar) {
      UnusedTracker.forProject(apiJars, _sourceSetsClassesDirs!!.files, toMinimize)
    } else {
      UnusedTracker.forProject(project.files(), project.files(), project.files())
    }
    return ShadowCopyAction(
      archiveFile.get().asFile,
      internalCompressor,
      documentationRegistry,
      metadataCharset,
      _transformers,
      _relocators,
      rootPatternSet,
      stats,
      isPreserveFileTimestamps,
      minimizeJar,
      unusedTracker,
    )
  }

  @TaskAction
  override fun copy() {
    if (enableRelocation) {
      configureRelocation(this, relocationPrefix)
    }
    from(includedDependencies)
    super.copy()
    logger.info(stats.toString())
  }

  override fun dependencies(action: Action<DependencyFilter>?): ShadowJar = apply {
    action?.execute(dependencyFilter)
  }

  override fun <T : Transformer> transform(clazz: Class<T>): ShadowJar {
    return transform(clazz, null)
  }

  override fun <T : Transformer> transform(clazz: Class<T>, action: Action<T>?): ShadowJar = apply {
    val transformer = clazz.getDeclaredConstructor().newInstance()
    addTransform(transformer, action)
  }

  override fun transform(transformer: Transformer): ShadowJar = apply {
    addTransform(transformer, null)
  }

  override fun mergeServiceFiles(): ShadowJar = runOrThrow {
    transform(ServiceFileTransformer::class.java)
  }

  override fun mergeServiceFiles(rootPath: String): ShadowJar = runOrThrow {
    transform(ServiceFileTransformer::class.java) { it.setPath(rootPath) }
  }

  override fun mergeServiceFiles(action: Action<ServiceFileTransformer>?): ShadowJar = runOrThrow {
    transform(ServiceFileTransformer::class.java, action)
  }

  override fun mergeGroovyExtensionModules(): ShadowJar = runOrThrow {
    transform(GroovyExtensionModuleTransformer::class.java)
  }

  override fun append(resourcePath: String): ShadowJar = runOrThrow {
    transform(AppendingTransformer::class.java) { it.resource = resourcePath }
  }

  override fun relocate(pattern: String, destination: String): ShadowJar {
    return relocate(pattern, destination, null)
  }

  override fun relocate(
    pattern: String,
    destination: String,
    action: Action<SimpleRelocator>?,
  ): ShadowJar = apply {
    val relocator = SimpleRelocator(pattern, destination, mutableListOf(), mutableListOf())
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

  private fun isCacheableTransform(clazz: Class<out Transformer>): Boolean {
    return clazz.isAnnotationPresent(CacheableTransformer::class.java)
  }

  private fun <T : Transformer> addTransform(transformer: T, action: Action<T>?) {
    action?.execute(transformer)
    _transformers.add(transformer)
  }

  private fun <R : Relocator> addRelocator(relocator: R, configure: Action<R>?) {
    configure?.execute(relocator)
    _relocators.add(relocator)
  }

  private fun isCacheableRelocator(clazz: Class<out Relocator>): Boolean {
    return clazz.isAnnotationPresent(CacheableRelocator::class.java)
  }

  private fun configureRelocation(target: ShadowJar, prefix: String) {
    target._configurations
      .asSequence()
      .flatMap { it.files }
      .flatMap { JarFile(it).entries().asSequence() }
      .filter { it.name.endsWith(".class") && it.name != "module-info.class" }
      .forEach {
        val pkg = it.name.substring(0, it.name.lastIndexOf('/')).replace('/', '.')
        target.relocate(pkg, "$prefix.$pkg")
      }
  }
}
