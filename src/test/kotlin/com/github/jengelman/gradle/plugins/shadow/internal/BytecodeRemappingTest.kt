package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import java.io.File
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.reflect.KClass
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.vafer.jdeb.shaded.objectweb.asm.AnnotationVisitor
import org.vafer.jdeb.shaded.objectweb.asm.ClassReader
import org.vafer.jdeb.shaded.objectweb.asm.ClassVisitor
import org.vafer.jdeb.shaded.objectweb.asm.ClassWriter
import org.vafer.jdeb.shaded.objectweb.asm.ConstantDynamic
import org.vafer.jdeb.shaded.objectweb.asm.FieldVisitor
import org.vafer.jdeb.shaded.objectweb.asm.Handle
import org.vafer.jdeb.shaded.objectweb.asm.Label
import org.vafer.jdeb.shaded.objectweb.asm.MethodVisitor
import org.vafer.jdeb.shaded.objectweb.asm.ModuleVisitor
import org.vafer.jdeb.shaded.objectweb.asm.Opcodes
import org.vafer.jdeb.shaded.objectweb.asm.Type

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
    val file = tempDir.resolve("broken.class").toFile().apply { writeText("not bytecode") }
    val details =
      object : FileCopyDetails by noOpDelegate() {
        override fun getPath(): String = path

        override fun getFile(): File = file
      }

    val failure = assertThrows<GradleException> { details.remapClass(relocators) }

    assertThat(failure.message)
      .isNotNull()
      .containsMatch("Error in (ASM|Class-File API) processing class $path".toRegex())
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
    val details = moduleInfoDetails { visitMainClass(originalMainClass) }

    val result = details.remapClass(relocators)
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
  fun modulePackagesAreRelocated() {
    val originalPackage = "com/github/jengelman/gradle/plugins/shadow/internal"
    val relocatedPackage = "com/example/relocated"
    val details = moduleInfoDetails {
      visitPackage(originalPackage)
      visitExport(originalPackage, 0)
      visitOpen(originalPackage, 0)
    }

    val result = details.remapClass(relocators)
    val packages = mutableListOf<String>()
    val exports = mutableListOf<String>()
    val opens = mutableListOf<String>()
    ClassReader(result)
      .accept(
        object : ClassVisitor(Opcodes.ASM9) {
          override fun visitModule(
            name: String,
            access: Int,
            version: String?,
          ): ModuleVisitor =
            object : ModuleVisitor(Opcodes.ASM9) {
              override fun visitPackage(packaze: String) {
                packages += packaze
              }

              override fun visitExport(
                packaze: String,
                access: Int,
                modules: Array<out String>?,
              ) {
                exports += packaze
              }

              override fun visitOpen(
                packaze: String,
                access: Int,
                modules: Array<out String>?,
              ) {
                opens += packaze
              }
            }
        },
        0,
      )

    assertThat(packages).isEqualTo(listOf(relocatedPackage))
    assertThat(exports).isEqualTo(listOf(relocatedPackage))
    assertThat(opens).isEqualTo(listOf(relocatedPackage))
  }

  @Test
  fun moduleClassReferencesAreRelocatedAndMetadataIsPreserved() {
    val originalService =
      $$"com/github/jengelman/gradle/plugins/shadow/internal/BytecodeRemappingTest$FixtureInterface"
    val originalProvider =
      $$"com/github/jengelman/gradle/plugins/shadow/internal/BytecodeRemappingTest$FixtureSubject"
    val relocatedService = $$"com/example/relocated/BytecodeRemappingTest$FixtureInterface"
    val relocatedProvider = $$"com/example/relocated/BytecodeRemappingTest$FixtureSubject"
    val details =
      moduleInfoDetails(Opcodes.ACC_OPEN, "1.0") {
        visitRequire("java.base", Opcodes.ACC_MANDATED, "25")
        visitExport("com/example/api", Opcodes.ACC_SYNTHETIC, "consumer.module")
        visitOpen("com/example/internal", Opcodes.ACC_MANDATED, "reflective.module")
        visitUse(originalService)
        visitProvide(originalService, originalProvider)
      }
    var moduleHeader: Triple<String, Int, String?>? = null
    val requires = mutableListOf<String>()
    val exports = mutableListOf<String>()
    val opens = mutableListOf<String>()
    val uses = mutableListOf<String>()
    val provides = mutableListOf<Pair<String, List<String>>>()

    ClassReader(details.remapClass(relocators))
      .accept(
        object : ClassVisitor(Opcodes.ASM9) {
          override fun visitModule(
            name: String,
            access: Int,
            version: String?,
          ): ModuleVisitor {
            moduleHeader = Triple(name, access, version)
            return object : ModuleVisitor(Opcodes.ASM9) {
              override fun visitRequire(module: String, access: Int, version: String?) {
                requires += "$module:$access:$version"
              }

              override fun visitExport(
                packaze: String,
                access: Int,
                modules: Array<out String>?,
              ) {
                exports += "$packaze:$access:${modules?.joinToString()}"
              }

              override fun visitOpen(
                packaze: String,
                access: Int,
                modules: Array<out String>?,
              ) {
                opens += "$packaze:$access:${modules?.joinToString()}"
              }

              override fun visitUse(service: String) {
                uses += service
              }

              override fun visitProvide(service: String, providers: Array<out String>) {
                provides += service to providers.toList()
              }
            }
          }
        },
        0,
      )

    assertThat(moduleHeader).isEqualTo(Triple("example.module", Opcodes.ACC_OPEN, "1.0"))
    assertThat(requires).isEqualTo(listOf("java.base:${Opcodes.ACC_MANDATED}:25"))
    assertThat(exports)
      .isEqualTo(listOf("com/example/api:${Opcodes.ACC_SYNTHETIC}:consumer.module"))
    assertThat(opens)
      .isEqualTo(listOf("com/example/internal:${Opcodes.ACC_MANDATED}:reflective.module"))
    assertThat(uses).isEqualTo(listOf(relocatedService))
    assertThat(provides).isEqualTo(listOf(relocatedService to listOf(relocatedProvider)))
  }

  @Test
  fun innerClassSimpleNameIsRelocated() {
    val originalInnerClass = "example/Outer\$Old"
    val relocatedInnerClass = "example/Outer\$New"
    val details =
      classDetails(originalInnerClass) {
        visitInnerClass(originalInnerClass, "example/Outer", "Old", Opcodes.ACC_PUBLIC)
      }
    val exactClassRelocator = setOf(exactPathRelocator(originalInnerClass, relocatedInnerClass))
    var innerClass: Triple<String, String?, String?>? = null

    val result = details.remapClass(exactClassRelocator)
    ClassReader(result)
      .accept(
        object : ClassVisitor(Opcodes.ASM9) {
          override fun visitInnerClass(
            name: String,
            outerName: String?,
            innerName: String?,
            access: Int,
          ) {
            innerClass = Triple(name, outerName, innerName)
          }
        },
        0,
      )

    assertThat(ClassReader(result).className).isEqualTo(relocatedInnerClass)
    assertThat(innerClass).isEqualTo(Triple(relocatedInnerClass, "example/Outer", "New"))
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun fieldStringConstantHonorsSkipSetting(skipStringConstants: Boolean) {
    val originalValue =
      $$"com.github.jengelman.gradle.plugins.shadow.internal.BytecodeRemappingTest$FixtureBase"
    val relocatedValue = $$"com.example.relocated.BytecodeRemappingTest$FixtureBase"
    val details =
      classDetails("example/Constants") {
        visitField(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
            "VALUE",
            "Ljava/lang/String;",
            null,
            originalValue,
          )
          .visitEnd()
      }
    val stringRelocators =
      setOf(
        SimpleRelocator(
          "com.github.jengelman.gradle.plugins.shadow.internal",
          "com.example.relocated",
          skipStringConstants = skipStringConstants,
        )
      )
    var fieldValue: Any? = null

    ClassReader(details.remapClass(stringRelocators))
      .accept(
        object : ClassVisitor(Opcodes.ASM9) {
          override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?,
          ): FieldVisitor? {
            if (name == "VALUE") fieldValue = value
            return null
          }
        },
        0,
      )

    assertThat(fieldValue).isEqualTo(if (skipStringConstants) originalValue else relocatedValue)
  }

  @Test
  fun constantPoolAndInvokeDynamicReferencesAreRelocated() {
    val originalType =
      $$"com/github/jengelman/gradle/plugins/shadow/internal/BytecodeRemappingTest$FixtureBase"
    val relocatedType = $$"com/example/relocated/BytecodeRemappingTest$FixtureBase"
    val originalDescriptor = "L$originalType;"
    val relocatedDescriptor = "L$relocatedType;"
    val methodHandle =
      Handle(Opcodes.H_INVOKESTATIC, originalType, "factory", "()$originalDescriptor", false)
    val constantBootstrap =
      Handle(
        Opcodes.H_INVOKESTATIC,
        "java/lang/invoke/ConstantBootstraps",
        "nullConstant",
        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
        false,
      )
    val callSiteBootstrap =
      Handle(
        Opcodes.H_INVOKESTATIC,
        originalType,
        "bootstrap",
        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
        false,
      )
    val callSiteBootstrapDescriptor = callSiteBootstrap.desc
    val details =
      classDetails("example/Constants") {
        visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "test", "()V", null, null).apply {
          visitCode()
          visitLdcInsn(Type.getType(originalDescriptor))
          visitInsn(Opcodes.POP)
          visitLdcInsn(Type.getMethodType("()$originalDescriptor"))
          visitInsn(Opcodes.POP)
          visitLdcInsn(methodHandle)
          visitInsn(Opcodes.POP)
          visitLdcInsn(ConstantDynamic("constant", originalDescriptor, constantBootstrap))
          visitInsn(Opcodes.POP)
          visitInvokeDynamicInsn(
            "call",
            "()$originalDescriptor",
            callSiteBootstrap,
            Type.getType(originalDescriptor),
            methodHandle,
          )
          visitInsn(Opcodes.POP)
          visitInsn(Opcodes.RETURN)
          visitMaxs(1, 0)
          visitEnd()
        }
      }
    val references = mutableListOf<String>()

    fun recordReference(value: Any) {
      when (value) {
        is Type -> references += "type:${value.descriptor}"
        is Handle -> references += "handle:${value.owner}:${value.desc}"
        is ConstantDynamic -> {
          references += "constant:${value.descriptor}"
          recordReference(value.bootstrapMethod)
          repeat(value.bootstrapMethodArgumentCount) {
            recordReference(value.getBootstrapMethodArgument(it))
          }
        }
      }
    }

    ClassReader(details.remapClass(relocators))
      .accept(
        object : ClassVisitor(Opcodes.ASM9) {
          override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
          ): MethodVisitor =
            object : MethodVisitor(Opcodes.ASM9) {
              override fun visitLdcInsn(value: Any) {
                recordReference(value)
              }

              override fun visitInvokeDynamicInsn(
                name: String,
                descriptor: String,
                bootstrapMethodHandle: Handle,
                vararg bootstrapMethodArguments: Any,
              ) {
                references += "invokedynamic:$descriptor"
                recordReference(bootstrapMethodHandle)
                bootstrapMethodArguments.forEach(::recordReference)
              }
            }
        },
        0,
      )

    assertThat(references).contains("type:$relocatedDescriptor")
    assertThat(references).contains("type:()$relocatedDescriptor")
    assertThat(references).contains("handle:$relocatedType:()$relocatedDescriptor")
    assertThat(references).contains("handle:$relocatedType:$callSiteBootstrapDescriptor")
    assertThat(references).contains("constant:$relocatedDescriptor")
    assertThat(references).contains("invokedynamic:()$relocatedDescriptor")
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

  private fun moduleInfoDetails(
    access: Int = 0,
    version: String? = null,
    configure: ModuleVisitor.() -> Unit,
  ): FileCopyDetails {
    val writer = ClassWriter(0)
    writer.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null)
    writer.visitModule("example.module", access, version).apply(configure).visitEnd()
    writer.visitEnd()
    return bytecodeDetails("module-info.class", writer.toByteArray())
  }

  private fun classDetails(
    internalName: String,
    configure: ClassWriter.() -> Unit,
  ): FileCopyDetails {
    val writer = ClassWriter(0)
    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
    writer.configure()
    writer.visitEnd()
    return bytecodeDetails("$internalName.class", writer.toByteArray())
  }

  private fun bytecodeDetails(path: String, bytes: ByteArray): FileCopyDetails {
    val file = tempDir.resolve(path).createParentDirectories().toFile().apply { writeBytes(bytes) }
    return object : FileCopyDetails by noOpDelegate() {
      override fun getPath(): String = path

      override fun getFile(): File = file
    }
  }

  private fun exactPathRelocator(original: String, relocated: String): Relocator =
    object : Relocator {
      override fun canRelocatePath(path: String): Boolean = path == original

      override fun relocatePath(context: RelocatePathContext): String = relocated

      override fun canRelocateClass(className: String): Boolean = false

      override fun relocateClass(context: RelocateClassContext): String = context.className

      override fun applyToSourceContent(sourceContent: String): String = sourceContent
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

  class FixtureGenericOuter<T> {
    inner class FixtureInner
  }

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

    fun methodWithNestedGeneric(arg: FixtureGenericOuter<FixtureBase>.FixtureInner) = Unit
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
