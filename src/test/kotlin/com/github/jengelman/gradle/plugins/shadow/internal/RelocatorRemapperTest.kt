package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import java.io.File
import java.lang.classfile.Attributes
import java.lang.classfile.ClassFile
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.reflect.KClass
import org.gradle.api.file.FileCopyDetails
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The cases reflect the cases in
 * [com.github.jengelman.gradle.plugins.shadow.relocation.RelocatorsTest], but operate on the
 * bytecode level to verify that the remapper correctly transforms class names in all relevant
 * bytecode structures.
 */
class RelocatorRemapperTest {
  @TempDir lateinit var tempDir: Path

  // Relocator used across all relocation tests: moves the test package to a distinct target.
  private val relocators =
    setOf(
      SimpleRelocator(
        "com.github.jengelman.gradle.plugins.shadow.internal",
        "com.example.relocated",
      )
    )

  // Internal name of the relocated FixtureBase for use in assertions.
  private val relocatedFixtureBase = $$"com/example/relocated/RelocatorRemapperTest$FixtureBase"

  @Test
  fun remapClassNotModified() {
    val details = FixtureSubject::class.toFileCopyDetails()
    // Relocator pattern does not match – original bytes must be returned as-is.
    val noMatchRelocators = setOf(SimpleRelocator("org.unrelated", "org.other"))

    val result = details.remapClass(noMatchRelocators)

    assertThat(result).isEqualTo(details.file.readBytes())
  }

  @Test
  fun remapClassNameIsRelocated() {
    val details = FixtureSubject::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    assertThat(classModel.thisClass().asInternalName())
      .isEqualTo($$"com/example/relocated/RelocatorRemapperTest$FixtureSubject")
  }

  @Test
  fun remapSuperclassIsRelocated() {
    val details = FixtureSubject::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    assertThat(classModel.superclass().get().asInternalName()).isEqualTo(relocatedFixtureBase)
  }

  @Test
  fun remapFieldDescriptorIsRelocated() {
    val details = FixtureSubject::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val fieldDescriptors = classModel.fields().map { it.fieldType().stringValue() }
    assertThat(fieldDescriptors).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun remapMethodDescriptorIsRelocated() {
    val details = FixtureSubject::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val methodDescriptors = classModel.methods().map { it.methodType().stringValue() }
    assertThat(methodDescriptors).contains("(L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun remapAnnotationIsRelocated() {
    val details = FixtureSubject::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val annotationsAttr = classModel.findAttribute(Attributes.runtimeVisibleAnnotations())
    assertThat(annotationsAttr.isPresent).isTrue()
    val annotationDescriptors =
      annotationsAttr.get().annotations().map { it.className().stringValue() }
    assertThat(annotationDescriptors)
      .contains($$"Lcom/example/relocated/RelocatorRemapperTest$FixtureAnnotation;")
  }

  @Test
  fun remapArrayFieldDescriptorIsRelocated() {
    val details = FixtureSubject::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val fieldDescriptors = classModel.fields().map { it.fieldType().stringValue() }
    assertThat(fieldDescriptors).contains("[L$relocatedFixtureBase;")
  }

  @Test
  fun remapArray2dFieldDescriptorIsRelocated() {
    val details = FixtureSubject::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val fieldDescriptors = classModel.fields().map { it.fieldType().stringValue() }
    assertThat(fieldDescriptors).contains("[[L$relocatedFixtureBase;")
  }

  @Test
  fun remapMethodMultipleArgsIsRelocated() {
    val details = FixtureSubject::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val methodDescriptors = classModel.methods().map { it.methodType().stringValue() }
    assertThat(methodDescriptors)
      .contains("(L$relocatedFixtureBase;L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun remapMethodPrimitivePlusClassIsRelocated() {
    val details = FixtureSubject::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val methodDescriptors = classModel.methods().map { it.methodType().stringValue() }
    assertThat(methodDescriptors).contains("(BL$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun remapBaseClassNameIsRelocated() {
    // Verify relocation also works on a simple class (FixtureBase has no fields/methods
    // referencing the target package beyond its own class name).
    val details = FixtureBase::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    assertThat(classModel.thisClass().asInternalName()).isEqualTo(relocatedFixtureBase)
  }

  private fun KClass<*>.toFileCopyDetails() =
    object : FileCopyDetails by noOpDelegate() {
      private val _path = java.name.replace('.', '/') + ".class"
      private val _file =
        tempDir
          .resolve(_path)
          .createParentDirectories()
          .also { requireResourceAsPath(_path).copyTo(it) }
          .toFile()

      override fun getPath(): String = _path

      override fun getFile(): File = _file
    }

  // ---------------------------------------------------------------------------
  // Fixture classes – declared as nested classes so their bytecode is compiled
  // into the test output directory and can be fetched via requireResourceAsPath.
  // ---------------------------------------------------------------------------

  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.CLASS)
  annotation class FixtureAnnotation

  open class FixtureBase

  @Suppress("unused") // Used by parsing bytecode.
  @FixtureAnnotation
  class FixtureSubject : FixtureBase() {
    val field: FixtureBase = FixtureBase()
    val arrayField: Array<FixtureBase> = emptyArray()
    val array2dField: Array<Array<FixtureBase>> = emptyArray()

    fun method(arg: FixtureBase): FixtureBase = arg

    fun methodMultiArgs(a: FixtureBase, b: FixtureBase): FixtureBase = a

    fun methodWithPrimitivePlusClass(b: Byte, arg: FixtureBase): FixtureBase = arg
  }
}
