package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker
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
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Action
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
  private val _transformers = mutableListOf<Transformer>()
  private val _relocators = mutableListOf<Relocator>()
  private val _configurations = mutableListOf<FileCollection>()
  private val _stats = ShadowStats()
  private val _includedDependencies = project.files(Callable { _dependencyFilter.resolve(_configurations) })

  @Transient
  private val dependencyFilterForMinimize = MinimizeDependencyFilter(project)

  private var minimizeJar = false
  private var _isEnableRelocation = false
  private var _relocationPrefix = ShadowBasePlugin.SHADOW
  private var _toMinimize: FileCollection? = null
  private var _apiJars: FileCollection? = null
  private var _sourceSetsClassesDirs: FileCollection? = null

  @Transient
  private var _dependencyFilter: DependencyFilter = DefaultDependencyFilter(project)

  init {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest = DefaultInheritManifest(services.get(FileResolver::class.java))

    inputs.property("minimize") { minimizeJar }
    outputs.doNotCacheIf("Has one or more transforms or relocators that are not cacheable") {
      _transformers.any { !isCacheableTransform(it::class.java) } ||
        _relocators.any { !isCacheableRelocator(it::class.java) }
    }
  }

  @get:Internal
  override val stats: ShadowStats get() = _stats

  @get:Classpath
  public val toMinimize: FileCollection
    get() {
      if (_toMinimize == null) {
        _toMinimize = if (minimizeJar) {
          dependencyFilterForMinimize.resolve(_configurations)
            .minus(apiJars)
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
          UnusedTracker.getApiJarsFromProject(project)
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
          project.extensions.getByType(SourceSetContainer::class.java).forEach { sourceSet ->
            allClassesDirs.from(sourceSet.output.classesDirs)
          }
        }
        _sourceSetsClassesDirs = allClassesDirs.filter { it.isDirectory }
      }
      return _sourceSetsClassesDirs!!
    }

  @get:Classpath
  public val includedDependencies: FileCollection get() = _includedDependencies

  @get:Internal
  public val rootPatternSet: PatternSet
    get() {
      return (mainSpec.buildRootResolver() as DefaultCopySpec.DefaultCopySpecResolver).patternSet
    }

  @get:Internal
  public val internalCompressor: ZipCompressor
    get() {
      return when (entryCompression) {
        ZipEntryCompression.DEFLATED -> DefaultZipCompressor(isZip64, ZipOutputStream.DEFLATED)
        ZipEntryCompression.STORED -> DefaultZipCompressor(isZip64, ZipOutputStream.STORED)
        else -> throw IllegalArgumentException("Unknown Compression type $entryCompression")
      }
    }

  @get:Nested
  public var transformers: List<Transformer>
    get() = _transformers
    set(value) {
      _transformers.clear()
      _transformers.addAll(value)
    }

  @get:Nested
  public var relocators: List<Relocator>
    get() = _relocators
    set(value) {
      _relocators.clear()
      _relocators.addAll(value)
    }

  @get:Classpath
  @get:Optional
  public var configurations: List<FileCollection>
    get() = _configurations
    set(value) {
      _configurations.clear()
      _configurations.addAll(value)
    }

  @get:Internal
  public var dependencyFilter: DependencyFilter
    get() = _dependencyFilter
    set(value) {
      _dependencyFilter = value
    }

  @get:Input
  public var isEnableRelocation: Boolean
    get() = _isEnableRelocation
    set(value) {
      _isEnableRelocation = value
    }

  @get:Input
  public var relocationPrefix: String
    get() = _relocationPrefix
    set(value) {
      _relocationPrefix = value
    }

  @Internal
  override fun getManifest(): InheritManifest = super.getManifest() as InheritManifest

  override fun minimize(): ShadowJar = apply {
    minimizeJar = true
  }

  override fun minimize(action: Action<DependencyFilter>?): ShadowJar = apply {
    minimize()
    action?.execute(dependencyFilterForMinimize)
  }

  override fun createCopyAction(): CopyAction {
    val documentationRegistry = services.get(DocumentationRegistry::class.java)
    val unusedTracker = if (minimizeJar) {
      UnusedTracker.forProject(apiJars, sourceSetsClassesDirs.files, toMinimize)
    } else {
      null
    }
    return ShadowCopyAction(
      archiveFile.get().asFile,
      internalCompressor,
      documentationRegistry,
      metadataCharset,
      _transformers,
      _relocators,
      rootPatternSet,
      _stats,
      isPreserveFileTimestamps,
      minimizeJar,
      unusedTracker,
    )
  }

  override fun dependencies(action: Action<DependencyFilter>?): ShadowJar = apply {
    action?.execute(_dependencyFilter)
  }

  override fun transform(clazz: Class<Transformer>): ShadowJar {
    return transform(clazz, null)
  }

  override fun <T : Transformer> transform(clazz: Class<T>, action: Action<T>?): ShadowJar = apply {
    val transformer = clazz.getDeclaredConstructor().newInstance()
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
        it.resource = resourcePath
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

  @TaskAction
  override fun copy() {
    if (_isEnableRelocation) {
      configureRelocation(this, _relocationPrefix)
    }
    from(_includedDependencies)
    super.copy()
    logger.info(_stats.toString())
  }

  private fun <R : Relocator> addRelocator(relocator: R, configure: Action<R>?) {
    configure?.execute(relocator)
    _relocators.add(relocator)
  }

  private fun <T : Transformer> addTransform(transformer: T, action: Action<T>?) {
    action?.execute(transformer)
    _transformers.add(transformer)
  }

  private fun isCacheableRelocator(clazz: Class<out Relocator>): Boolean {
    return clazz.isAnnotationPresent(CacheableRelocator::class.java)
  }

  private fun isCacheableTransform(clazz: Class<out Transformer>): Boolean {
    return clazz.isAnnotationPresent(CacheableTransformer::class.java)
  }

  private fun configureRelocation(target: ShadowJar, prefix: String) {
    val packages = mutableSetOf<String>()
    target.configurations.forEach { configuration ->
      configuration.files.forEach { jarFile ->
        JarFile(jarFile).use { jf ->
          jf.entries().asSequence().forEach { entry ->
            if (entry.name.endsWith(".class") && entry.name != "module-info.class") {
              packages.add(entry.name.substringBeforeLast('/').replace('/', '.'))
            }
          }
        }
      }
    }
    packages.forEach {
      target.relocate(it, "$prefix.$it")
    }
  }
}
