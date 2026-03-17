package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import java.io.File
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.reflect.KClass
import org.gradle.api.file.FileCopyDetails
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

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

    val classNode = result.toClassNode()
    assertThat(classNode.name)
      .isEqualTo($$"com/example/relocated/BytecodeRemappingTest$FixtureSubject")
  }

  @Test
  fun annotationIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val annotationDescriptors = classNode.visibleAnnotations.orEmpty().map { it.desc }
    assertThat(annotationDescriptors)
      .contains($$"Lcom/example/relocated/BytecodeRemappingTest$FixtureAnnotation;")
  }

  @Test
  fun baseClassNameIsRelocated() {
    // Verify relocation also works on a simple class (FixtureBase has no fields/methods
    // referencing the target package beyond its own class name).
    val details = FixtureBase::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    val classNode = result.toClassNode()
    assertThat(classNode.name).isEqualTo(relocatedFixtureBase)
  }

  @Test
  fun superclassIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    assertThat(classNode.superName).isEqualTo(relocatedFixtureBase)
  }

  @Test
  fun fieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val fieldDescriptors = classNode.fields.map { it.desc }
    assertThat(fieldDescriptors).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun arrayFieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val fieldDescriptors = classNode.fields.map { it.desc }
    assertThat(fieldDescriptors).contains("[L$relocatedFixtureBase;")
  }

  @Test
  fun array2dFieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val fieldDescriptors = classNode.fields.map { it.desc }
    assertThat(fieldDescriptors).contains("[[L$relocatedFixtureBase;")
  }

  @Test
  fun methodDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val methodDescriptors = classNode.methods.map { it.desc }
    assertThat(methodDescriptors).contains("(L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun methodMultipleArgsIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val methodDescriptors = classNode.methods.map { it.desc }
    assertThat(methodDescriptors)
      .contains("(L$relocatedFixtureBase;L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @ParameterizedTest
  @ValueSource(chars = ['B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z'])
  fun primitivePlusClassMethodIsRelocated(primitiveDescriptor: Char) {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val methodDescriptors = classNode.methods.map { it.desc }
    assertThat(methodDescriptors)
      .contains("(${primitiveDescriptor}L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun stringConstantIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val stringConstants = classNode.allStringConstants()
    assertThat(stringConstants)
      .contains($$"com.example.relocated.BytecodeRemappingTest$FixtureBase")
  }

  @Test
  fun stringConstantNotRelocatedWhenSkipEnabled() {
    val skipRelocators =
      setOf(
        SimpleRelocator(
          "com.github.jengelman.gradle.plugins.shadow.internal",
          "com.example.relocated",
          skipStringConstants = true,
        )
      )
    val result = fixtureSubjectDetails.remapClass(skipRelocators)

    val classNode = result.toClassNode()
    val stringConstants = classNode.allStringConstants()
    assertThat(stringConstants)
      .doesNotContain($$"com.example.relocated.BytecodeRemappingTest$FixtureBase")
  }

  @Test
  fun multiClassDescriptorStringConstantIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val stringConstants = classNode.allStringConstants()
    // Verify that two adjacent class references in a single string constant are both relocated
    // (regression test for the issue-1403 pattern).
    assertThat(stringConstants)
      .contains(
        $$"()Lcom/example/relocated/BytecodeRemappingTest$FixtureBase;Lcom/example/relocated/BytecodeRemappingTest$FixtureBase;"
      )
  }

  @Test
  fun interfaceIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    assertThat(classNode.interfaces)
      .contains($$"com/example/relocated/BytecodeRemappingTest$FixtureInterface")
  }

  @Test
  fun signatureIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val method = classNode.methods.first { it.name == "methodWithGeneric" }
    assertThat(method.signature).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun localVariableIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classNode = result.toClassNode()
    val method = classNode.methods.first { it.name == "method" }
    val descriptors = method.localVariables.orEmpty().map { it.desc }
    assertThat(descriptors).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun instructionIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val classReader = ClassReader(result)
    var hasRelocatedCheckCast = false
    var hasRelocatedInvoke = false

    classReader.accept(
      object : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
          access: Int,
          name: String,
          desc: String,
          signature: String?,
          exceptions: Array<out String>?,
        ): MethodVisitor? {
          if (name != "methodWithCheckCast") return null
          return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitTypeInsn(opcode: Int, type: String) {
              if (opcode == Opcodes.CHECKCAST && type == relocatedFixtureBase) {
                hasRelocatedCheckCast = true
              }
            }

            override fun visitMethodInsn(
              opcode: Int,
              owner: String,
              name: String,
              descriptor: String,
              isInterface: Boolean,
            ) {
              if (owner == relocatedFixtureBase) {
                hasRelocatedInvoke = true
              }
            }
          }
        }
      },
      0,
    )

    assertThat(hasRelocatedCheckCast).isTrue()
    assertThat(hasRelocatedInvoke).isTrue()
  }

  private fun ClassNode.allStringConstants(): List<String> {
    val strings = mutableListOf<String>()
    this.accept(
      object : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
          access: Int,
          name: String,
          desc: String,
          signature: String?,
          exceptions: Array<out String>?,
        ): MethodVisitor {
          return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitLdcInsn(value: Any?) {
              if (value is String) strings.add(value)
            }
          }
        }

        override fun visitField(
          access: Int,
          name: String,
          desc: String,
          signature: String?,
          value: Any?,
        ): FieldVisitor? {
          if (value is String) strings.add(value)
          return null
        }
      }
    )
    return strings
  }

  private fun ByteArray.toClassNode(): ClassNode =
    ClassNode().also { ClassReader(this).accept(it, 0) }

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
    val multiClassDescriptor: String =
      $$"()Lcom/github/jengelman/gradle/plugins/shadow/internal/BytecodeRemappingTest$FixtureBase;Lcom/github/jengelman/gradle/plugins/shadow/internal/BytecodeRemappingTest$FixtureBase;"

    fun method(arg: FixtureBase): FixtureBase = arg

    fun methodMultiArgs(a: FixtureBase, b: FixtureBase): FixtureBase = a

    fun methodWithPrimitivePlusClass(b: Byte, arg: FixtureBase): FixtureBase = arg

    fun methodWithCharPlusClass(c: Char, arg: FixtureBase): FixtureBase = arg

    fun methodWithDoublePlusClass(d: Double, arg: FixtureBase): FixtureBase = arg

    fun methodWithFloatPlusClass(f: Float, arg: FixtureBase): FixtureBase = arg

    fun methodWithIntPlusClass(i: Int, arg: FixtureBase): FixtureBase = arg

    fun methodWithLongPlusClass(l: Long, arg: FixtureBase): FixtureBase = arg

    fun methodWithShortPlusClass(s: Short, arg: FixtureBase): FixtureBase = arg

    fun methodWithBooleanPlusClass(z: Boolean, arg: FixtureBase): FixtureBase = arg

    fun methodWithCheckCast(arg: Any): FixtureBase {
      (arg as FixtureBase).toString()
      return arg
    }

    fun methodWithGeneric(list: List<FixtureBase>): FixtureBase = list[0]
  }
}
