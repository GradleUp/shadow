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
import java.lang.classfile.instruction.InvokeInstruction
import java.lang.classfile.instruction.TypeCheckInstruction
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
class BytecodeRemappingTest {
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
  private val relocatedFixtureBase = $$"com/example/relocated/BytecodeRemappingTest$FixtureBase"

  private val fixtureSubjectDetails
    get() = FixtureSubject::class.toFileCopyDetails()

  @Test
  fun classNotModified() {
    val details = fixtureSubjectDetails
    // Relocator pattern does not match – original bytes must be returned as-is.
    val noMatchRelocators = setOf(SimpleRelocator("org.unrelated", "org.other"))

    val result = details.remapClass(noMatchRelocators)

    assertThat(result).isEqualTo(details.file.readBytes())
  }

  @Test
  fun classNameIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    assertThat(classModel.thisClass().asInternalName())
      .isEqualTo($$"com/example/relocated/BytecodeRemappingTest$FixtureSubject")
  }

  @Test
  fun annotationIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val annotationsAttr = classModel.findAttribute(Attributes.runtimeVisibleAnnotations())
    assertThat(annotationsAttr.isPresent).isTrue()
    val annotationDescriptors =
      annotationsAttr.get().annotations().map { it.className().stringValue() }
    assertThat(annotationDescriptors)
      .contains($$"Lcom/example/relocated/BytecodeRemappingTest$FixtureAnnotation;")
  }

  @Test
  fun baseClassNameIsRelocated() {
    // Verify relocation also works on a simple class (FixtureBase has no fields/methods
    // referencing the target package beyond its own class name).
    val details = FixtureBase::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    assertThat(classModel.thisClass().asInternalName()).isEqualTo(relocatedFixtureBase)
  }

  @Test
  fun superclassIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    assertThat(classModel.superclass().get().asInternalName()).isEqualTo(relocatedFixtureBase)
  }

  @Test
  fun fieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val fieldDescriptors = classModel.fields().map { it.fieldType().stringValue() }
    assertThat(fieldDescriptors).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun arrayFieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val fieldDescriptors = classModel.fields().map { it.fieldType().stringValue() }
    assertThat(fieldDescriptors).contains("[L$relocatedFixtureBase;")
  }

  @Test
  fun array2dFieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val fieldDescriptors = classModel.fields().map { it.fieldType().stringValue() }
    assertThat(fieldDescriptors).contains("[[L$relocatedFixtureBase;")
  }

  @Test
  fun methodDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val methodDescriptors = classModel.methods().map { it.methodType().stringValue() }
    assertThat(methodDescriptors).contains("(L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun methodMultipleArgsIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val methodDescriptors = classModel.methods().map { it.methodType().stringValue() }
    assertThat(methodDescriptors)
      .contains("(L$relocatedFixtureBase;L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun methodPrimitivePlusClassIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val methodDescriptors = classModel.methods().map { it.methodType().stringValue() }
    assertThat(methodDescriptors).contains("(BL$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun stringConstantIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    // Find the constant string in the bytecode.
    val stringConstants =
      classModel.constantPool().mapNotNull { entry ->
        if (entry is java.lang.classfile.constantpool.StringEntry) entry.stringValue() else null
      }
    assertThat(stringConstants).contains("com.example.relocated.BytecodeRemappingTest\$FixtureBase")
  }

  @Test
  fun interfaceIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val interfaces = classModel.interfaces().map { it.asInternalName() }
    assertThat(interfaces)
      .contains($$"com/example/relocated/BytecodeRemappingTest$FixtureInterface")
  }

  @Test
  fun signatureIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val method = classModel.methods().first { it.methodName().stringValue() == "methodWithGeneric" }
    val signatureAttr = method.findAttribute(Attributes.signature())
    assertThat(signatureAttr.isPresent).isTrue()
    val sig = signatureAttr.get().signature().stringValue()
    assertThat(sig).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun localVariableIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val method = classModel.methods().first { it.methodName().stringValue() == "method" }
    val code = method.code().get()
    val lvt = code.findAttribute(Attributes.localVariableTable())
    assertThat(lvt.isPresent).isTrue()
    val descriptors = lvt.get().localVariables().map { it.type().stringValue() }
    assertThat(descriptors).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun instructionIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classModel = ClassFile.of().parse(result)
    val method =
      classModel.methods().first { it.methodName().stringValue() == "methodWithCheckCast" }
    val code = method.code().get()

    val hasRelocatedCheckCast =
      code.elementStream().anyMatch { element ->
        element is TypeCheckInstruction && element.type().asInternalName() == relocatedFixtureBase
      }
    assertThat(hasRelocatedCheckCast).isTrue()

    val hasRelocatedInvoke =
      code.elementStream().anyMatch { element ->
        element is InvokeInstruction && element.owner().asInternalName() == relocatedFixtureBase
      }
    assertThat(hasRelocatedInvoke).isTrue()
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

  interface FixtureInterface

  open class FixtureBase

  @Suppress("unused") // Used by parsing bytecode.
  @FixtureAnnotation
  class FixtureSubject : FixtureBase(), FixtureInterface {
    val field: FixtureBase = FixtureBase()
    val arrayField: Array<FixtureBase> = emptyArray()
    val array2dField: Array<Array<FixtureBase>> = emptyArray()
    val stringConstant: String =
      $$"com.github.jengelman.gradle.plugins.shadow.internal.BytecodeRemappingTest$FixtureBase"

    fun method(arg: FixtureBase): FixtureBase = arg

    fun methodMultiArgs(a: FixtureBase, b: FixtureBase): FixtureBase = a

    fun methodWithPrimitivePlusClass(b: Byte, arg: FixtureBase): FixtureBase = arg

    fun methodWithCheckCast(arg: Any): FixtureBase {
      (arg as FixtureBase).toString()
      return arg
    }

    fun methodWithGeneric(list: List<FixtureBase>): FixtureBase = list[0]
  }
}
