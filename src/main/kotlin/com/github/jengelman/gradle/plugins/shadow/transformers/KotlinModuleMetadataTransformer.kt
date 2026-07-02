package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.checkDupStrategy
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import javax.inject.Inject
import kotlin.metadata.jvm.KmModule
import kotlin.metadata.jvm.KmPackageParts
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.util.PatternSet

/**
 * A resource transformer that relocates package parts within Kotlin module metadata files
 * (`.kotlin_module`).
 */
@CacheableTransformer
public open class KotlinModuleMetadataTransformer(
  final override val objectFactory: ObjectFactory,
  patternSet: PatternSet,
) : PatternFilterableResourceTransformer(patternSet) {

  @Inject
  public constructor(
    objectFactory: ObjectFactory
  ) : this(
    objectFactory,
    PatternSet().include("**/*.kotlin_module"),
  )

  private val moduleEntries = mutableMapOf<String, ByteArray>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return super.canTransformResource(element).also { flag -> checkDupStrategy(flag, element) }
  }

  @OptIn(UnstableMetadataApi::class)
  override fun transform(context: TransformerContext) {
    val bytes = context.inputStream.readBytes()
    if (context.relocators.isEmpty()) {
      moduleEntries[context.path] = bytes
      return
    }
    val kmMetadata = KotlinModuleMetadata.read(bytes)
    val newKmModule =
      KmModule().apply {
        // We don't need to relocate the nested properties in `optionalAnnotationClasses`, there
        // is a very special use case for Kotlin Multiplatform.
        optionalAnnotationClasses += kmMetadata.kmModule.optionalAnnotationClasses
        packageParts +=
          kmMetadata.kmModule.packageParts.map { (pkg, parts) ->
            val relocatedPkg = context.relocators.relocateClass(pkg)
            val relocatedParts =
              KmPackageParts(
                parts.fileFacades.mapTo(mutableListOf()) { context.relocators.relocatePath(it) },
                parts.multiFileClassParts.entries.associateTo(mutableMapOf()) { (name, facade) ->
                  context.relocators.relocatePath(name) to context.relocators.relocatePath(facade)
                },
              )
            relocatedPkg to relocatedParts
          }
      }
    val newKmMetadata = KotlinModuleMetadata(newKmModule, kmMetadata.version)
    val newBytes = newKmMetadata.write()

    val entryName =
      when {
        // Nothing changed, so keep the original (already relocated) path.
        newBytes.contentEquals(bytes) -> context.path
        // Content changed but path didn't, so rename to avoid name clash. The filename does not
        // matter to the compiler.
        else -> context.path.replace(".kotlin_module", ".shadow.kotlin_module")
      }

    moduleEntries[entryName] = newBytes
  }

  override fun hasTransformedResource(): Boolean = moduleEntries.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    moduleEntries.forEach { (path, bytes) ->
      os.putNextEntry(zipEntry(path, preserveFileTimestamps))
      os.write(bytes)
      os.closeEntry()
    }
  }
}
