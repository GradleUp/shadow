package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker.Companion.getApiJarsFromProject
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
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
class ShadowJar :
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

  var transformers: List<Transformer>
    @Nested get() = _transformers
    set(value) {
      _transformers.clear()
      _transformers.addAll(value)
    }

  var relocators: List<Relocator>
    @Nested get() = _relocators
    set(value) {
      _relocators.clear()
      _relocators.addAll(value)
    }

  var configurations: List<FileCollection>
    @Classpath get() = _configurations
    set(value) {
      _configurations.clear()
      _configurations.addAll(value)
    }

  @get:Input
  var enableRelocation: Boolean = false

  @get:Input
  var relocationPrefix: String = "shadow"

  @get:Internal
  var dependencyFilter: DependencyFilter = DefaultDependencyFilter(project)

  @get:Internal
  override val stats: ShadowStats = ShadowStats()

  @get:Internal
  val internalCompressor: ZipCompressor
    get() {
      return when (entryCompression) {
        ZipEntryCompression.DEFLATED -> DefaultZipCompressor(isZip64, ZipOutputStream.DEFLATED)
        ZipEntryCompression.STORED -> DefaultZipCompressor(isZip64, ZipOutputStream.STORED)
        else -> throw IllegalArgumentException("Unknown Compression type $entryCompression")
      }
    }

  @get:Classpath
  val toMinimize: FileCollection
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
  val apiJars: FileCollection
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
  val sourceSetsClassesDirs: FileCollection
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
  val includedDependencies: ConfigurableFileCollection
    get() = project.files(
      Callable {
        dependencyFilter.resolve(_configurations)
      },
    )

  @get:Internal
  val rootPatternSet: PatternSet
    get() {
      return (mainSpec.buildRootResolver() as DefaultCopySpec.DefaultCopySpecResolver).patternSet
    }

  override fun minimize(): ShadowJar {
    minimizeJar = true
    return this
  }

  override fun minimize(action: Action<DependencyFilter>?): ShadowJar {
    minimize()
    action?.execute(dependencyFilterForMinimize)
    return this
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

  override fun dependencies(action: Action<DependencyFilter>?): ShadowJar {
    action?.execute(dependencyFilter)
    return this
  }

  override fun <T : Transformer> transform(clazz: Class<T>): ShadowJar {
    return transform(clazz, null)
  }

  override fun <T : Transformer> transform(clazz: Class<T>, action: Action<T>?): ShadowJar {
    val transformer = clazz.getDeclaredConstructor().newInstance()
    addTransform(transformer, action)
    return this
  }

  override fun transform(transformer: Transformer): ShadowJar {
    addTransform(transformer, null)
    return this
  }

  override fun mergeServiceFiles(): ShadowJar {
    return try {
      transform(ServiceFileTransformer::class.java)
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  override fun mergeServiceFiles(rootPath: String): ShadowJar {
    return try {
      transform(ServiceFileTransformer::class.java) { it.setPath(rootPath) }
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  override fun mergeServiceFiles(action: Action<ServiceFileTransformer>?): ShadowJar {
    return try {
      transform(ServiceFileTransformer::class.java, action)
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  override fun mergeGroovyExtensionModules(): ShadowJar {
    return try {
      transform(GroovyExtensionModuleTransformer::class.java)
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  override fun append(resourcePath: String): ShadowJar {
    return try {
      transform(AppendingTransformer::class.java) { it.resource = resourcePath }
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  override fun relocate(pattern: String, destination: String): ShadowJar {
    return relocate(pattern, destination, null)
  }

  override fun relocate(pattern: String, destination: String, action: Action<SimpleRelocator>?): ShadowJar {
    val relocator = SimpleRelocator(pattern, destination, mutableListOf(), mutableListOf())
    addRelocator(relocator, action)
    return this
  }

  override fun relocate(relocator: Relocator): ShadowJar {
    addRelocator(relocator, null)
    return this
  }

  override fun relocate(clazz: Class<out Relocator>): ShadowJar {
    return relocate(clazz, null)
  }

  override fun <R : Relocator> relocate(clazz: Class<R>, action: Action<R>?): ShadowJar {
    val relocator = clazz.getDeclaredConstructor().newInstance()
    addRelocator(relocator, action)
    return this
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
