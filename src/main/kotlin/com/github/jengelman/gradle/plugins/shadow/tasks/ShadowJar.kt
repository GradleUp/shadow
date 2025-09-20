package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultInheritManifest
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.fileCollection
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
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
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.DuplicatesStrategy.EXCLUDE
import org.gradle.api.file.DuplicatesStrategy.FAIL
import org.gradle.api.file.DuplicatesStrategy.INCLUDE
import org.gradle.api.file.DuplicatesStrategy.INHERIT
import org.gradle.api.file.DuplicatesStrategy.WARN
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
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.options.Option
import org.gradle.language.base.plugins.LifecycleBasePlugin

@CacheableTask
public abstract class ShadowJar : Jar() {
  private val dependencyFilterForMinimize = MinimizeDependencyFilter(project)
  private val shadowDependencies = project.provider { project.files(project.configurations.shadow) }

  init {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Create a combined JAR of project and runtime dependencies"

    // https://github.com/gradle/gradle/blob/df5bc230c57db70aa3f6909403e5f89d7efde531/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/copy/DuplicateHandlingCopyActionDecorator.java#L55-L64
    duplicatesStrategy = EXCLUDE
    manifest = DefaultInheritManifest(project)

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

  /**
   * Main class attribute to add to manifest.
   *
   * This property will be used as a fallback if there is no explicit `Main-Class` attribute set for the [ShadowJar]
   * task or the main [Jar] task.
   *
   * Defaults to `null`.
   */
  @get:Optional
  @get:Input
  @get:Option(option = "main-class", description = "Main class attribute to add to manifest.")
  public open val mainClass: Property<String> = objectFactory.property()

  /**
   * Fails build if the ZIP entries in the shadowed JAR are duplicate.
   *
   * This is related to setting [getDuplicatesStrategy] to [FAIL] but there are some differences:
   * - It only checks the entries in the shadowed jar, not the input files.
   * - It works with setting [getDuplicatesStrategy] to any value.
   * - Usually used with setting [getDuplicatesStrategy] to [INCLUDE] or [WARN].
   * - It provides a stricter check before the JAR is created.
   *
   * Defaults to `false`.
   */
  @get:Input
  @get:Option(option = "fail-on-duplicate-entries", description = "Fails build if the ZIP entries in the shadowed JAR are duplicate.")
  public open val failOnDuplicateEntries: Property<Boolean> = objectFactory.property(false)

  /**
   * Adds the [java.util.jar.Attributes.Name.MULTI_RELEASE] attribute to the manifest of the shadow JAR if any
   * dependencies contain the attribute.
   *
   * Defaults to `true`.
   */
  @get:Input
  @get:Option(option = "add-multi-release-attribute", description = "Adds the multi-release attribute to the manifest if any dependencies contain it.")
  public open val addMultiReleaseAttribute: Property<Boolean> = objectFactory.property(true)

  @Suppress("DEPRECATION") // TODO: replace the usage of deprecated InheritManifest.
  @Internal
  override fun getManifest(): InheritManifest = super.getManifest() as InheritManifest

  @Input // Trigger task executions after includes changed.
  override fun getIncludes(): MutableSet<String> = super.getIncludes()

  @Input // Trigger task executions after excludes changed.
  override fun getExcludes(): MutableSet<String> = super.getExcludes()

  /**
   * Returns the strategy to use when trying to copy more than one file to the same destination.
   *
   * This global strategy can be overridden for individual files by using [filesMatching].
   *
   * The default value is [EXCLUDE]. Different strategies will lead to different results for `foo/bar` files in the JARs to be merged:
   *
   * - [EXCLUDE]: The **first** `foo/bar` file will be included in the final JAR.
   * - [FAIL]: **Fail** the build with a `DuplicateFileCopyingException` if there are duplicate `foo/bar` files.
   * - [INCLUDE]: **Duplicate** `foo/bar` entries will be included in the final JAR.
   * - [INHERIT]: **Fail** the build with an exception like `Entry .* is a duplicate but no duplicate handling strategy has been set`.
   * - [WARN]: **Warn** about duplicates in the build log, this behaves exactly as [INHERIT] otherwise.
   *
   * **NOTE:** The strategy takes precedence over transforming and relocating.
   * Some [ResourceTransformer]s like [ServiceFileTransformer] will not work as expected with setting the strategy to
   * [EXCLUDE] (the default), as the duplicate resource files fed for them are excluded beforehand.
   * Want [ResourceTransformer]s and the strategy to work together? See more details in the
   * [Handling Duplicates Strategy](https://gradleup.com/shadow/configuration/merging/#handling-duplicates-strategy) section.
   *
   * @see [eachFile]
   * @see [filesMatching]
   * @see [filesNotMatching]
   * @see [DuplicatesStrategy]
   * @see [CopySpec.duplicatesStrategy]
   */
  override fun getDuplicatesStrategy(): DuplicatesStrategy = super.getDuplicatesStrategy()

  @get:Inject
  protected abstract val archiveOperations: ArchiveOperations

  /**
   * Enable [minimizeJar] and execute the [action] with the [DependencyFilter] for minimize.
   */
  @JvmOverloads
  public open fun minimize(action: Action<DependencyFilter> = Action {}) {
    minimizeJar.set(true)
    action.execute(dependencyFilterForMinimize)
  }

  /**
   * Extra dependency operations to be applied in the shadow steps.
   */
  public open fun dependencies(action: Action<DependencyFilter>) {
    action.execute(dependencyFilter.get())
  }

  /**
   * Merge Java services files with [rootPath].
   */
  public open fun mergeServiceFiles(rootPath: String) {
    mergeServiceFiles { it.path = rootPath }
  }

  /**
   * Merge Java services files with [action].
   */
  @JvmOverloads
  public open fun mergeServiceFiles(action: Action<ServiceFileTransformer> = Action {}) {
    transform(ServiceFileTransformer::class.java, action)
  }

  /**
   * Merge Groovy extension modules (`META-INF/**/org.codehaus.groovy.runtime.ExtensionModule`).
   */
  public open fun mergeGroovyExtensionModules() {
    transform(GroovyExtensionModuleTransformer::class.java, action = {})
  }

  /**
   * Append contents to a resource in the jar.
   *
   * e.g. `append("resources/application.yml", "\n---\n")` for merging `resources/application.yml` files.
   *
   * @param resourcePath The path to the resource in the jar.
   * @param separator The separator to use between the original content and the appended content, defaults to [AppendingTransformer.DEFAULT_SEPARATOR] (`\n`).
   */
  @JvmOverloads
  public open fun append(resourcePath: String, separator: String = AppendingTransformer.DEFAULT_SEPARATOR) {
    transform(AppendingTransformer::class.java) {
      it.resource.set(resourcePath)
      it.separator.set(separator)
    }
  }

  /**
   * Relocate classes and resources matching [pattern] to [destination] using [SimpleRelocator].
   */
  @JvmOverloads
  public open fun relocate(
    pattern: String,
    destination: String,
    action: Action<SimpleRelocator> = Action {},
  ) {
    val relocator = SimpleRelocator(pattern, destination)
    addRelocator(relocator, action)
  }

  /**
   * Relocate classes and resources using a [Relocator].
   */
  @JvmOverloads
  public open fun <R : Relocator> relocate(clazz: Class<R>, action: Action<R> = Action {}) {
    val relocator = clazz.getDeclaredConstructor().newInstance()
    addRelocator(relocator, action)
  }

  /**
   * Relocate classes and resources using a [Relocator].
   */
  @JvmOverloads
  public open fun <R : Relocator> relocate(relocator: R, action: Action<R> = Action {}) {
    addRelocator(relocator, action)
  }

  /**
   * Relocate classes and resources using a [Relocator].
   */
  @JvmSynthetic
  public inline fun <reified R : Relocator> relocate(action: Action<R> = Action {}) {
    relocate(R::class.java, action)
  }

  /**
   * Transform resources using a [ResourceTransformer].
   */
  @JvmOverloads
  public open fun <T : ResourceTransformer> transform(clazz: Class<T>, action: Action<T> = Action {}) {
    addTransform(clazz.create(objectFactory), action)
  }

  /**
   * Transform resources using a [ResourceTransformer].
   */
  @JvmOverloads
  public open fun <T : ResourceTransformer> transform(transformer: T, action: Action<T> = Action {}) {
    addTransform(transformer, action)
  }

  /**
   * Transform resources using a [ResourceTransformer].
   */
  @JvmSynthetic
  public inline fun <reified T : ResourceTransformer> transform(action: Action<T> = Action {}) {
    transform(T::class.java, action)
  }

  @TaskAction
  override fun copy() {
    includedDependencies.files.forEach { file ->
      when {
        file.isDirectory -> {
          from(file)
        }
        file.extension.equals("aar", ignoreCase = true) -> {
          val message = """
            Shadowing AAR file is not supported.
            Please exclude dependency artifact: $file
            or use Android Fused Library plugin instead. See https://developer.android.com/build/publish-library/fused-library.
          """.trimIndent()
          error(message)
        }
        else -> {
          from(archiveOperations.zipTree(file))
        }
      }
    }
    injectManifestAttributes()
    super.copy()
  }

  override fun createCopyAction(): CopyAction {
    val zosProvider = { destination: File ->
      try {
        val entryCompressionMethod = when (entryCompression) {
          ZipEntryCompression.DEFLATED -> ZipOutputStream.DEFLATED
          ZipEntryCompression.STORED -> ZipOutputStream.STORED
        }
        val stream = if (entryCompressionMethod == ZipOutputStream.STORED) {
          ZipOutputStream(destination)
        } else {
          // Improve performance by avoiding lots of small writes to the file system.
          // It is not possible to do this with STORED entries as the implementation requires a RandomAccessFile to update the CRC after write.
          ZipOutputStream(destination.outputStream().buffered())
        }
        stream.apply {
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
      zipFile = archiveFile.get().asFile,
      zosProvider = zosProvider,
      transformers = transformers.get(),
      relocators = relocators.get() + packageRelocators,
      unusedClasses = unusedClasses,
      preserveFileTimestamps = isPreserveFileTimestamps,
      failOnDuplicateEntries = failOnDuplicateEntries.get(),
      metadataCharset,
    )
  }

  private fun <R : Relocator> addRelocator(relocator: R, action: Action<R>) {
    action.execute(relocator)
    relocators.add(relocator)
  }

  private fun <T : ResourceTransformer> addTransform(transformer: T, action: Action<T>) {
    action.execute(transformer)
    transformers.add(transformer)
  }

  private val packageRelocators: List<SimpleRelocator>
    get() {
      if (enableAutoRelocation.get()) {
        logger.info("Adding auto relocation packages in the dependencies with prefix '${relocationPrefix.get()}'.")
      } else {
        logger.info("Skipping package relocators as auto relocation is disabled.")
        return emptyList()
      }
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

  private fun injectManifestAttributes() {
    val mainClassValue = mainClass.orNull
    when {
      manifest.attributes.contains(mainClassAttributeKey) -> {
        logger.info("Skipping adding $mainClassAttributeKey attribute to the manifest as it is already set.")
      }
      mainClassValue.isNullOrEmpty() -> {
        logger.info("Skipping adding $mainClassAttributeKey attribute to the manifest as it is empty.")
      }
      else -> {
        manifest.attributes[mainClassAttributeKey] = mainClassValue
        logger.info("Adding $mainClassAttributeKey attribute to the manifest with value '$mainClassValue'.")
      }
    }

    val classPathAttr = manifest.attributes[classPathAttributeKey]?.toString().orEmpty()
    val shadowFiles = shadowDependencies.get()
    if (!shadowFiles.isEmpty) {
      val attrs = listOf(classPathAttr) + shadowFiles.map { it.name }
      manifest.attributes[classPathAttributeKey] = attrs.joinToString(" ").trim()
    }

    if (addMultiReleaseAttribute.get()) {
      logger.info("Adding $multiReleaseAttributeKey attribute to the manifest if any dependencies contain it.")
    } else {
      logger.info("Skipping adding $multiReleaseAttributeKey attribute to the manifest as it is disabled.")
      return
    }
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

  public companion object {
    public const val SHADOW_JAR_TASK_NAME: String = "shadowJar"

    @get:JvmSynthetic
    public inline val TaskContainer.shadowJar: TaskProvider<ShadowJar>
      get() = named(SHADOW_JAR_TASK_NAME, ShadowJar::class.java)

    internal fun Project.registerShadowJarCommon(
      jarTask: TaskProvider<Jar>,
      action: (ShadowJar) -> Unit,
    ): TaskProvider<ShadowJar> {
      return tasks.register(SHADOW_JAR_TASK_NAME, ShadowJar::class.java) { task ->
        task.archiveClassifier.set("all")
        task.exclude(
          "META-INF/INDEX.LIST",
          "META-INF/*.SF",
          "META-INF/*.DSA",
          "META-INF/*.RSA",
          // module-info.class in Multi-Release folders.
          "META-INF/versions/**/module-info.class",
          "module-info.class",
        )

        task.manifest = DefaultInheritManifest(
          project,
          @Suppress("EagerGradleConfiguration") // The ctor doesn't support Provider.
          jarTask.get().manifest,
        )

        action(task)
      }.also { task ->
        // Can't use `named` directly as the task is optional or may not exist when the plugin is applied.
        // Using Spec<String> applies the action to the task if it is added later.
        tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME::equals).configureEach {
          it.dependsOn(task)
        }
      }
    }
  }
}
