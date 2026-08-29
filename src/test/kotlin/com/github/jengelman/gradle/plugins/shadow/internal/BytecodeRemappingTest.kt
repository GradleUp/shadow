package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.reflect.KClass
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.vafer.jdeb.shaded.objectweb.asm.AnnotationVisitor
import org.vafer.jdeb.shaded.objectweb.asm.ClassReader
import org.vafer.jdeb.shaded.objectweb.asm.ClassVisitor
import org.vafer.jdeb.shaded.objectweb.asm.ClassWriter
import org.vafer.jdeb.shaded.objectweb.asm.FieldVisitor
import org.vafer.jdeb.shaded.objectweb.asm.Label
import org.vafer.jdeb.shaded.objectweb.asm.MethodVisitor
import org.vafer.jdeb.shaded.objectweb.asm.ModuleVisitor
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
  fun asmFailureIsWrappedWithClassPath() {
    val path = "broken/Example.class"
    val file = tempDir.resolve(path).createParentDirectories().apply { writeText("not bytecode") }

    assertFailure { file.toFileCopyDetails().remapClass(relocators) }
      .isInstanceOf<GradleException>()
      .hasMessage("Error in ASM processing class $path")
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
      .isEqualTo(
        listOf(
          $$"Lcom/example/relocated/BytecodeRemappingTest$FixtureAnnotation;",
          "Lkotlin/Metadata;",
        )
      )
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun annotationStringValueIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val annotation =
      result.classInfo().annotations.first {
        it.descriptor == $$"Lcom/example/relocated/BytecodeRemappingTest$FixtureAnnotation;"
      }
    val stringValue =
      annotation.values["stringValue"] as? String ?: error("stringValue must be String")
    val stringArrayValue =
      annotation.values["stringArrayValue"] as? Array<String>
        ?: error("stringArrayValue must be Array<String>")
    assertThat(stringValue).isEqualTo($$"com.example.relocated.BytecodeRemappingTest$FixtureBase")
    assertThat(stringArrayValue)
      .isEqualTo(arrayOf($$"com/example/relocated/BytecodeRemappingTest$FixtureBase"))
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun kotlinMetadataIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val metadataAnnotation =
      result.classInfo().annotations.single { it.descriptor == "Lkotlin/Metadata;" }
    val d1 = metadataAnnotation.values["d1"] as? Array<String> ?: error("d1 must be Array<String>")
    val d2 = metadataAnnotation.values["d2"] as? Array<String> ?: error("d2 must be Array<String>")
    val mv = metadataAnnotation.values["mv"] as? IntArray ?: error("mv must be IntArray")

    assertThat(d2[0]).isEqualTo($$"Lcom/example/relocated/BytecodeRemappingTest$FixtureSubject;")
    assertThat(d2[1]).isEqualTo("L$relocatedFixtureBase;")

    val metadata =
      Metadata(
        kind = metadataAnnotation.values["k"] as? Int ?: 1,
        metadataVersion = mv,
        data1 = d1,
        data2 = d2,
        extraString = metadataAnnotation.values["xs"] as? String ?: "",
        packageName = metadataAnnotation.values["pn"] as? String ?: "",
        extraInt = metadataAnnotation.values["xi"] as? Int ?: 0,
      )
    val kmClass = (KotlinClassMetadata.readStrict(metadata) as KotlinClassMetadata.Class).kmClass
    assertThat(kmClass.name).isEqualTo("com/example/relocated/BytecodeRemappingTest.FixtureSubject")
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

    val field = result.classInfo().fieldData.first { it.name == "field" }
    assertThat(field.descriptor).isEqualTo("L$relocatedFixtureBase;")
  }

  @Test
  fun arrayFieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val field = result.classInfo().fieldData.first { it.name == "arrayField" }
    assertThat(field.descriptor).isEqualTo("[L$relocatedFixtureBase;")
  }

  @Test
  fun array2dFieldDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val field = result.classInfo().fieldData.first { it.name == "array2dField" }
    assertThat(field.descriptor).isEqualTo("[[L$relocatedFixtureBase;")
  }

  @Test
  fun methodDescriptorIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "method" }
    assertThat(method.descriptor).isEqualTo("(L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun methodMultipleArgsIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "methodMultiArgs" }
    assertThat(method.descriptor)
      .isEqualTo("(L$relocatedFixtureBase;L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @ParameterizedTest
  @CsvSource(
    "methodWithPrimitivePlusClass, B",
    "methodWithCharPlusClass, C",
    "methodWithDoublePlusClass, D",
    "methodWithFloatPlusClass, F",
    "methodWithIntPlusClass, I",
    "methodWithLongPlusClass, J",
    "methodWithShortPlusClass, S",
    "methodWithBooleanPlusClass, Z",
  )
  fun primitivePlusClassMethodIsRelocated(methodName: String, primitiveDescriptor: Char) {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == methodName }
    assertThat(method.descriptor)
      .isEqualTo("(${primitiveDescriptor}L$relocatedFixtureBase;)L$relocatedFixtureBase;")
  }

  @Test
  fun stringConstantIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "<init>" }
    assertThat(method.stringConstants[0])
      .isEqualTo($$"com.example.relocated.BytecodeRemappingTest$FixtureBase")
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

    val method = result.classInfo().methodData.first { it.name == "<init>" }
    assertThat(method.stringConstants[0])
      .isEqualTo(
        $$"com.github.jengelman.gradle.plugins.shadow.internal.BytecodeRemappingTest$FixtureBase"
      )
  }

  @Test
  fun multiClassDescriptorStringConstantIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    // Verify that two adjacent class references in a single string constant are both relocated
    // (regression test for the issue-1403 pattern).
    val method = result.classInfo().methodData.first { it.name == "<init>" }
    assertThat(method.stringConstants[1])
      .isEqualTo(
        $$"()Lcom/example/relocated/BytecodeRemappingTest$FixtureBase;Lcom/example/relocated/BytecodeRemappingTest$FixtureBase;"
      )
  }

  @Test
  fun interfaceIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    assertThat(ClassReader(result).interfaces.toList())
      .isEqualTo(listOf($$"com/example/relocated/BytecodeRemappingTest$FixtureInterface"))
  }

  @Test
  fun signatureIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "methodWithGeneric" }
    assertThat(checkNotNull(method.signature))
      .isEqualTo(
        $$"(Ljava/util/List<+Lcom/example/relocated/BytecodeRemappingTest$FixtureBase;>;)Lcom/example/relocated/BytecodeRemappingTest$FixtureBase;"
      )
  }

  @Test
  fun nestedClassSignatureIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "methodWithNestedGeneric" }
    assertThat(checkNotNull(method.signature))
      .isEqualTo(
        $$"(Lcom/example/relocated/BytecodeRemappingTest$FixtureGenericOuter<Lcom/example/relocated/BytecodeRemappingTest$FixtureBase;>.FixtureInner;)V"
      )
  }

  @Test
  fun moduleMainClassIsRelocated() {
    val originalMainClass =
      $$"com/github/jengelman/gradle/plugins/shadow/internal/BytecodeRemappingTest$FixtureBase"
    val writer = ClassWriter(0)
    writer.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null)
    writer.visitModule("example.module", 0, null).apply { visitMainClass(originalMainClass) }
    writer.visitEnd()
    val file = tempDir.resolve("module-info.class").apply { writeBytes(writer.toByteArray()) }
    val result = file.toFileCopyDetails().remapClass(relocators)
    var remappedMainClass: String? = null
    ClassReader(result)
      .accept(
        object : ClassVisitor(Opcodes.ASM9) {
          override fun visitModule(
            name: String,
            access: Int,
            version: String?,
          ): ModuleVisitor =
            object : ModuleVisitor(Opcodes.ASM9) {
              override fun visitMainClass(mainClass: String) {
                remappedMainClass = mainClass
              }
            }
        },
        0,
      )

    assertThat(remappedMainClass).isEqualTo(relocatedFixtureBase)
  }

  @Test
  fun localVariableIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "method" }
    assertThat(method.localVarDescriptors)
      .isEqualTo(
        listOf(
          $$"Lcom/example/relocated/BytecodeRemappingTest$FixtureSubject;",
          "L$relocatedFixtureBase;",
        )
      )
  }

  @Test
  fun instructionIsRelocated() {
    val result = fixtureSubjectDetails.remapClass(relocators)

    val method = result.classInfo().methodData.first { it.name == "methodWithCheckCast" }
    assertThat(method.checkcastTargets)
      .isEqualTo(listOf(relocatedFixtureBase, relocatedFixtureBase))
    assertThat(method.invokeOwners)
      .isEqualTo(listOf("kotlin/jvm/internal/Intrinsics", relocatedFixtureBase))
  }

  private fun Path.toFileCopyDetails() =
    object : FileCopyDetails by noOpDelegate() {

      override fun getPath(): String = relativeTo(tempDir).invariantSeparatorsPathString

      override fun getFile(): File = toFile()

      override fun open(): InputStream = this@toFileCopyDetails.inputStream()
    }

  private fun KClass<*>.toFileCopyDetails(): FileCopyDetails {
    val path = "${java.name.replace('.', '/')}.class"
    val file =
      tempDir.resolve(path).createParentDirectories().also {
        requireResourceAsPath(path).copyTo(it)
      }
    return file.toFileCopyDetails()
  }

  // ---------------------------------------------------------------------------
  // Fixture classes – declared as nested classes so their bytecode is compiled
  // into the test output directory and can be fetched via requireResourceAsPath.
  // ---------------------------------------------------------------------------

  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.CLASS)
  @Suppress("unused")
  annotation class FixtureAnnotation(
    val stringValue: String = "",
    val stringArrayValue: Array<String> = [],
  )

  interface FixtureInterface

  open class FixtureBase

  @Suppress("unused")
  class FixtureGenericOuter<T> {
    @Suppress("RedundantInnerClassModifier") inner class FixtureInner
  }

  @Suppress("unused") // Used by parsing bytecode.
  @FixtureAnnotation(
    stringValue =
      $$"com.github.jengelman.gradle.plugins.shadow.internal.BytecodeRemappingTest$FixtureBase",
    stringArrayValue =
      [$$"com/github/jengelman/gradle/plugins/shadow/internal/BytecodeRemappingTest$FixtureBase"],
  )
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

    fun methodWithNestedGeneric(arg: FixtureGenericOuter<FixtureBase>.FixtureInner) = Unit
  }
}

@Suppress("SpellCheckingInspection")
private fun ByteArray.classInfo(): ClassBytecodeInfo {
  val annotationDescs = mutableListOf<String>()
  val annotations = mutableListOf<ClassBytecodeInfo.AnnotationBytecodeInfo>()
  val fields = mutableListOf<ClassBytecodeInfo.FieldBytecodeInfo>()
  val methods = mutableListOf<ClassBytecodeInfo.MethodBytecodeInfo>()

  ClassReader(this)
    .accept(
      object : ClassVisitor(Opcodes.ASM9) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
          annotationDescs.add(descriptor)
          val values = mutableMapOf<String, Any?>()
          annotations.add(ClassBytecodeInfo.AnnotationBytecodeInfo(descriptor, values))
          return object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String?, value: Any?) {
              if (name != null) values[name] = value
            }

            override fun visitArray(name: String?): AnnotationVisitor {
              val arrayElements = mutableListOf<Any?>()
              return object : AnnotationVisitor(Opcodes.ASM9) {
                override fun visit(name: String?, value: Any?) {
                  arrayElements.add(value)
                }

                override fun visitEnd() {
                  if (name != null) {
                    values[name] =
                      if (arrayElements.all { it is String }) {
                        arrayElements.filterIsInstance<String>().toTypedArray()
                      } else {
                        arrayElements.toTypedArray()
                      }
                  }
                }
              }
            }
          }
        }

        override fun visitField(
          access: Int,
          name: String,
          descriptor: String,
          signature: String?,
          value: Any?,
        ): FieldVisitor? {
          fields.add(ClassBytecodeInfo.FieldBytecodeInfo(name, descriptor))
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

  return ClassBytecodeInfo(annotationDescs, annotations, fields, methods)
}

private data class ClassBytecodeInfo(
  val annotationDescriptors: List<String>,
  val annotations: List<AnnotationBytecodeInfo>,
  val fieldData: List<FieldBytecodeInfo>,
  val methodData: List<MethodBytecodeInfo>,
) {
  data class AnnotationBytecodeInfo(
    val descriptor: String,
    val values: Map<String, Any?>,
  )

  data class FieldBytecodeInfo(
    val name: String,
    val descriptor: String,
  )

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
