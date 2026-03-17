package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
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
import org.vafer.jdeb.shaded.objectweb.asm.AnnotationVisitor
import org.vafer.jdeb.shaded.objectweb.asm.ClassReader
import org.vafer.jdeb.shaded.objectweb.asm.ClassVisitor
import org.vafer.jdeb.shaded.objectweb.asm.FieldVisitor
import org.vafer.jdeb.shaded.objectweb.asm.Label
import org.vafer.jdeb.shaded.objectweb.asm.MethodVisitor
import org.vafer.jdeb.shaded.objectweb.asm.Opcodes

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

    assertThat(ClassReader(result).className)
      .isEqualTo($$"com/example/relocated/BytecodeRemappingTest$FixtureSubject")
  }

  @Test
  fun annotationIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(result.classInfo().annotationDescriptors)
      .contains($$"Lcom/example/relocated/BytecodeRemappingTest$FixtureAnnotation;")
  }

  @Test
  fun baseClassNameIsRelocated() {
    // Verify relocation also works on a simple class (FixtureBase has no fields/methods
    // referencing the target package beyond its own class name).
    val details = FixtureBase::class.toFileCopyDetails()

    val result = details.remapClass(relocators)

    assertThat(ClassReader(result).className).isEqualTo(relocatedFixtureBase)
  }

  @Test
  fun superclassIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(ClassReader(result).superName).isEqualTo(relocatedFixtureBase)
  }

  @Test
  fun fieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(result.classInfo().fieldDescriptors).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun arrayFieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(result.classInfo().fieldDescriptors).contains("[L$relocatedFixtureBase;")
  }

  @Test
  fun array2dFieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(result.classInfo().fieldDescriptors).contains("[[L$relocatedFixtureBase;")
  }

  @Test
  fun methodDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(result.classInfo().methodDescriptors)
      .contains("(L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun methodMultipleArgsIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(result.classInfo().methodDescriptors)
      .contains("(L$relocatedFixtureBase;L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @ParameterizedTest
  @ValueSource(chars = ['B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z'])
  fun primitivePlusClassMethodIsRelocated(primitiveDescriptor: Char) {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(result.classInfo().methodDescriptors)
      .contains("(${primitiveDescriptor}L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun stringConstantIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(result.classInfo().stringConstants)
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

    assertThat(result.classInfo().stringConstants)
      .doesNotContain($$"com.example.relocated.BytecodeRemappingTest$FixtureBase")
  }

  @Test
  fun multiClassDescriptorStringConstantIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    // Verify that two adjacent class references in a single string constant are both relocated
    // (regression test for the issue-1403 pattern).
    assertThat(result.classInfo().stringConstants)
      .contains(
        $$"()Lcom/example/relocated/BytecodeRemappingTest$FixtureBase;Lcom/example/relocated/BytecodeRemappingTest$FixtureBase;"
      )
  }

  @Test
  fun interfaceIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(ClassReader(result).interfaces.toList())
      .contains($$"com/example/relocated/BytecodeRemappingTest$FixtureInterface")
  }

  @Test
  fun signatureIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "methodWithGeneric" }
    assertThat(checkNotNull(method.signature)).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun localVariableIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "method" }
    assertThat(method.localVarDescriptors).contains("L$relocatedFixtureBase;")
  }

  @Test
  fun instructionIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "methodWithCheckCast" }
    assertThat(method.checkcastTargets).contains(relocatedFixtureBase)
    assertThat(method.invokeOwners).contains(relocatedFixtureBase)
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

private data class ClassBytecodeInfo(
  val annotationDescriptors: List<String>,
  val fieldDescriptors: List<String>,
  val methodData: List<MethodBytecodeInfo>,
) {
  val methodDescriptors = methodData.map { it.descriptor }
  val stringConstants = methodData.flatMap { it.stringConstants }

  data class MethodBytecodeInfo(
    val name: String,
    val descriptor: String,
    val signature: String?,
    val localVarDescriptors: List<String>,
    @Suppress("SpellCheckingInspection") val checkcastTargets: List<String>,
    val invokeOwners: List<String>,
    val stringConstants: List<String>,
  )
}

@Suppress("SpellCheckingInspection")
private fun ByteArray.classInfo(): ClassBytecodeInfo {
  val annotationDescs = mutableListOf<String>()
  val fieldDescs = mutableListOf<String>()
  val methods = mutableListOf<ClassBytecodeInfo.MethodBytecodeInfo>()

  ClassReader(this)
    .accept(
      object : ClassVisitor(Opcodes.ASM9) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
          annotationDescs.add(descriptor)
          return null
        }

        override fun visitField(
          access: Int,
          name: String,
          descriptor: String,
          signature: String?,
          value: Any?,
        ): FieldVisitor? {
          fieldDescs.add(descriptor)
          return null
        }

        override fun visitMethod(
          access: Int,
          name: String,
          descriptor: String,
          signature: String?,
          exceptions: Array<out String>?,
        ): MethodVisitor {
          val localVarDescs = mutableListOf<String>()
          val checkcastTargets = mutableListOf<String>()
          val invokeOwners = mutableListOf<String>()
          val stringConsts = mutableListOf<String>()

          return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitLocalVariable(
              name: String,
              descriptor: String,
              signature: String?,
              start: Label,
              end: Label,
              index: Int,
            ) {
              localVarDescs.add(descriptor)
            }

            override fun visitTypeInsn(opcode: Int, type: String) {
              if (opcode == Opcodes.CHECKCAST) checkcastTargets.add(type)
            }

            override fun visitMethodInsn(
              opcode: Int,
              owner: String,
              name: String,
              descriptor: String,
              isInterface: Boolean,
            ) {
              invokeOwners.add(owner)
            }

            override fun visitLdcInsn(value: Any) {
              if (value is String) stringConsts.add(value)
            }

            override fun visitEnd() {
              methods.add(
                ClassBytecodeInfo.MethodBytecodeInfo(
                  name,
                  descriptor,
                  signature,
                  localVarDescs.toList(),
                  checkcastTargets.toList(),
                  invokeOwners.toList(),
                  stringConsts.toList(),
                )
              )
            }
          }
        }
      },
      0,
    )

  return ClassBytecodeInfo(annotationDescs, fieldDescs, methods)
}
