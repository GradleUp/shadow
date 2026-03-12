package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import java.lang.classfile.Annotation
import java.lang.classfile.AnnotationElement
import java.lang.classfile.AnnotationValue
import java.lang.classfile.ClassFile
import java.lang.classfile.ClassSignature
import java.lang.classfile.ClassTransform
import java.lang.classfile.CodeModel
import java.lang.classfile.CodeTransform
import java.lang.classfile.FieldModel
import java.lang.classfile.FieldTransform
import java.lang.classfile.Interfaces
import java.lang.classfile.MethodModel
import java.lang.classfile.MethodSignature
import java.lang.classfile.MethodTransform
import java.lang.classfile.Signature
import java.lang.classfile.Superclass
import java.lang.classfile.TypeAnnotation
import java.lang.classfile.attribute.AnnotationDefaultAttribute
import java.lang.classfile.attribute.ConstantValueAttribute
import java.lang.classfile.attribute.EnclosingMethodAttribute
import java.lang.classfile.attribute.ExceptionsAttribute
import java.lang.classfile.attribute.InnerClassInfo
import java.lang.classfile.attribute.InnerClassesAttribute
import java.lang.classfile.attribute.ModuleAttribute
import java.lang.classfile.attribute.ModuleExportInfo
import java.lang.classfile.attribute.ModuleOpenInfo
import java.lang.classfile.attribute.ModuleProvideInfo
import java.lang.classfile.attribute.NestHostAttribute
import java.lang.classfile.attribute.NestMembersAttribute
import java.lang.classfile.attribute.PermittedSubclassesAttribute
import java.lang.classfile.attribute.RecordAttribute
import java.lang.classfile.attribute.RecordComponentInfo
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute
import java.lang.classfile.attribute.SignatureAttribute
import java.lang.classfile.constantpool.StringEntry
import java.lang.classfile.instruction.ConstantInstruction
import java.lang.classfile.instruction.ExceptionCatch
import java.lang.classfile.instruction.FieldInstruction
import java.lang.classfile.instruction.InvokeDynamicInstruction
import java.lang.classfile.instruction.InvokeInstruction
import java.lang.classfile.instruction.LocalVariable
import java.lang.classfile.instruction.LocalVariableType
import java.lang.classfile.instruction.NewMultiArrayInstruction
import java.lang.classfile.instruction.NewObjectInstruction
import java.lang.classfile.instruction.NewReferenceArrayInstruction
import java.lang.classfile.instruction.TypeCheckInstruction
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDesc
import java.lang.constant.DirectMethodHandleDesc
import java.lang.constant.DynamicCallSiteDesc
import java.lang.constant.DynamicConstantDesc
import java.lang.constant.MethodHandleDesc
import java.lang.constant.MethodTypeDesc
import java.lang.constant.PackageDesc
import kotlin.jvm.optionals.getOrNull
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails

@Suppress("unused") // Used by Multi-Release JARs for Java 24+.
internal fun FileCopyDetails.remapClass(relocators: Set<Relocator>): ByteArray =
  file.readBytes().let { bytes ->
    var modified = false
    val remapper = RelocatorRemapper(relocators) { modified = true }

    val newBytes =
      try {
        val classFile = ClassFile.of()
        val classModel = classFile.parse(bytes)
        val newClassDesc = remapper.mapClassDesc(classModel.thisClass().asSymbol())
        classFile.transformClass(classModel, newClassDesc, remapper.asClassTransform())
      } catch (t: Throwable) {
        throw GradleException("Error in Class-File API processing class $path", t)
      }

    // If we didn't need to change anything, keep the original bytes as-is.
    if (modified) newBytes else bytes
  }

private class RelocatorRemapper(
  private val relocators: Set<Relocator>,
  private val onModified: () -> Unit,
) {
  fun asClassTransform(): ClassTransform = ClassTransform { clb, cle ->
    when (cle) {
      is FieldModel ->
        clb.withField(
          cle.fieldName().stringValue(),
          mapClassDesc(ClassDesc.ofDescriptor(cle.fieldType().stringValue())),
        ) { fb ->
          fb.withFlags(cle.flags().flagsMask()).transform(cle, asFieldTransform())
        }
      is MethodModel ->
        clb.withMethod(
          cle.methodName().stringValue(),
          cle.methodTypeSymbol().remap(),
          cle.flags().flagsMask(),
        ) { mb ->
          mb.transform(cle, asMethodTransform())
        }
      is Superclass -> clb.withSuperclass(mapClassDesc(cle.superclassEntry().asSymbol()))
      is Interfaces ->
        clb.withInterfaceSymbols(cle.interfaces().map { mapClassDesc(it.asSymbol()) })
      is SignatureAttribute -> clb.with(SignatureAttribute.of(cle.asClassSignature().remap()))
      is InnerClassesAttribute ->
        clb.with(
          InnerClassesAttribute.of(
            cle.classes().map { ici ->
              InnerClassInfo.of(
                mapClassDesc(ici.innerClass().asSymbol()),
                ici.outerClass().map { mapClassDesc(it.asSymbol()) },
                ici.innerName().map { it.stringValue() },
                ici.flagsMask(),
              )
            }
          )
        )
      is EnclosingMethodAttribute ->
        clb.with(
          EnclosingMethodAttribute.of(
            mapClassDesc(cle.enclosingClass().asSymbol()),
            cle.enclosingMethodName().map { it.stringValue() },
            cle
              .enclosingMethodType()
              .map { MethodTypeDesc.ofDescriptor(it.stringValue()) }
              .map { it.remap() },
          )
        )
      is RecordAttribute -> clb.with(RecordAttribute.of(cle.components().map { it.remap() }))
      is ModuleAttribute ->
        clb.with(
          ModuleAttribute.of(
            cle.moduleName(),
            cle.moduleFlagsMask(),
            cle.moduleVersion().getOrNull(),
            cle.requires(),
            cle.exports().map { mei ->
              ModuleExportInfo.of(
                clb
                  .constantPool()
                  .packageEntry(
                    PackageDesc.ofInternalName(map(mei.exportedPackage().asSymbol().internalName()))
                  ),
                mei.exportsFlagsMask(),
                mei.exportsTo(),
              )
            },
            cle.opens().map { moi ->
              ModuleOpenInfo.of(
                clb
                  .constantPool()
                  .packageEntry(
                    PackageDesc.ofInternalName(map(moi.openedPackage().asSymbol().internalName()))
                  ),
                moi.opensFlagsMask(),
                moi.opensTo(),
              )
            },
            cle.uses().map { clb.constantPool().classEntry(mapClassDesc(it.asSymbol())) },
            cle.provides().map { mp ->
              ModuleProvideInfo.of(
                mapClassDesc(mp.provides().asSymbol()),
                mp.providesWith().map { mapClassDesc(it.asSymbol()) },
              )
            },
          )
        )
      is NestHostAttribute ->
        clb.with(NestHostAttribute.of(mapClassDesc(cle.nestHost().asSymbol())))
      is NestMembersAttribute ->
        clb.with(
          NestMembersAttribute.ofSymbols(cle.nestMembers().map { mapClassDesc(it.asSymbol()) })
        )
      is PermittedSubclassesAttribute ->
        clb.with(
          PermittedSubclassesAttribute.ofSymbols(
            cle.permittedSubclasses().map { mapClassDesc(it.asSymbol()) }
          )
        )
      is RuntimeVisibleAnnotationsAttribute ->
        clb.with(RuntimeVisibleAnnotationsAttribute.of(cle.annotations().map { it.remap() }))
      is RuntimeInvisibleAnnotationsAttribute ->
        clb.with(RuntimeInvisibleAnnotationsAttribute.of(cle.annotations().map { it.remap() }))
      is RuntimeVisibleTypeAnnotationsAttribute ->
        clb.with(RuntimeVisibleTypeAnnotationsAttribute.of(cle.annotations().map { it.remap() }))
      is RuntimeInvisibleTypeAnnotationsAttribute ->
        clb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(cle.annotations().map { it.remap() }))
      else -> clb.with(cle)
    }
  }

  fun mapClassDesc(desc: ClassDesc): ClassDesc {
    when {
      desc.isArray -> return mapClassDesc(desc.componentType()).arrayType()
      desc.isPrimitive -> return desc
    }
    val internalName = desc.descriptorString().let { it.substring(1, it.length - 1) }
    val mappedInternalName = map(internalName)
    return if (internalName == mappedInternalName) desc
    else ClassDesc.ofDescriptor("L$mappedInternalName;")
  }

  private fun asFieldTransform() = FieldTransform { fb, fe ->
    when (fe) {
      is ConstantValueAttribute -> {
        val constant = fe.constant()
        if (constant is StringEntry) {
          val remapped = map(constant.stringValue(), true)
          fb.with(ConstantValueAttribute.of(fb.constantPool().stringEntry(remapped)))
        } else {
          fb.with(fe)
        }
      }
      is SignatureAttribute -> fb.with(SignatureAttribute.of(fe.asTypeSignature().remap()))
      is RuntimeVisibleAnnotationsAttribute ->
        fb.with(RuntimeVisibleAnnotationsAttribute.of(fe.annotations().map { it.remap() }))
      is RuntimeInvisibleAnnotationsAttribute ->
        fb.with(RuntimeInvisibleAnnotationsAttribute.of(fe.annotations().map { it.remap() }))
      is RuntimeVisibleTypeAnnotationsAttribute ->
        fb.with(RuntimeVisibleTypeAnnotationsAttribute.of(fe.annotations().map { it.remap() }))
      is RuntimeInvisibleTypeAnnotationsAttribute ->
        fb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(fe.annotations().map { it.remap() }))
      else -> fb.with(fe)
    }
  }

  private fun asMethodTransform() = MethodTransform { mb, me ->
    when (me) {
      is AnnotationDefaultAttribute ->
        mb.with(AnnotationDefaultAttribute.of(me.defaultValue().remap()))
      is CodeModel -> mb.transformCode(me, asCodeTransform())
      is ExceptionsAttribute ->
        mb.with(ExceptionsAttribute.ofSymbols(me.exceptions().map { mapClassDesc(it.asSymbol()) }))
      is SignatureAttribute -> mb.with(SignatureAttribute.of(me.asMethodSignature().remap()))
      is RuntimeVisibleAnnotationsAttribute ->
        mb.with(RuntimeVisibleAnnotationsAttribute.of(me.annotations().map { it.remap() }))
      is RuntimeInvisibleAnnotationsAttribute ->
        mb.with(RuntimeInvisibleAnnotationsAttribute.of(me.annotations().map { it.remap() }))
      is RuntimeVisibleParameterAnnotationsAttribute ->
        mb.with(
          RuntimeVisibleParameterAnnotationsAttribute.of(
            me.parameterAnnotations().map { pas -> pas.map { it.remap() } }
          )
        )
      is RuntimeInvisibleParameterAnnotationsAttribute ->
        mb.with(
          RuntimeInvisibleParameterAnnotationsAttribute.of(
            me.parameterAnnotations().map { pas -> pas.map { it.remap() } }
          )
        )
      is RuntimeVisibleTypeAnnotationsAttribute ->
        mb.with(RuntimeVisibleTypeAnnotationsAttribute.of(me.annotations().map { it.remap() }))
      is RuntimeInvisibleTypeAnnotationsAttribute ->
        mb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(me.annotations().map { it.remap() }))
      else -> mb.with(me)
    }
  }

  private fun asCodeTransform() = CodeTransform { cob, coe ->
    when (coe) {
      is FieldInstruction ->
        cob.fieldAccess(
          coe.opcode(),
          mapClassDesc(coe.owner().asSymbol()),
          coe.name().stringValue(),
          mapClassDesc(coe.typeSymbol()),
        )
      is InvokeInstruction ->
        cob.invoke(
          coe.opcode(),
          mapClassDesc(coe.owner().asSymbol()),
          coe.name().stringValue(),
          coe.typeSymbol().remap(),
          coe.isInterface,
        )
      is InvokeDynamicInstruction ->
        cob.invokedynamic(
          DynamicCallSiteDesc.of(
            coe.bootstrapMethod().remap(),
            coe.name().stringValue(),
            coe.typeSymbol().remap(),
            *coe.bootstrapArgs().map { it.remap() }.toTypedArray(),
          )
        )
      is NewObjectInstruction -> cob.new_(mapClassDesc(coe.className().asSymbol()))
      is NewReferenceArrayInstruction -> cob.anewarray(mapClassDesc(coe.componentType().asSymbol()))
      is NewMultiArrayInstruction ->
        cob.multianewarray(mapClassDesc(coe.arrayType().asSymbol()), coe.dimensions())
      is TypeCheckInstruction ->
        cob.with(TypeCheckInstruction.of(coe.opcode(), mapClassDesc(coe.type().asSymbol())))
      is ExceptionCatch ->
        cob.exceptionCatch(
          coe.tryStart(),
          coe.tryEnd(),
          coe.handler(),
          coe.catchType().map { cob.constantPool().classEntry(mapClassDesc(it.asSymbol())) },
        )
      is LocalVariable ->
        cob.localVariable(
          coe.slot(),
          coe.name().stringValue(),
          mapClassDesc(coe.typeSymbol()),
          coe.startScope(),
          coe.endScope(),
        )
      is LocalVariableType ->
        cob.localVariableType(
          coe.slot(),
          coe.name().stringValue(),
          coe.signatureSymbol().remap(),
          coe.startScope(),
          coe.endScope(),
        )
      is ConstantInstruction.LoadConstantInstruction -> {
        val value = coe.constantEntry().constantValue()
        val name = value.javaClass.name
        @Suppress("CAST_NEVER_SUCCEEDS")
        when (name) {
          "java.lang.String" -> {
            val s = value.toString()
            cob.ldc(cob.constantPool().stringEntry(map(s, mapLiterals = true)))
          }
          "java.lang.Integer" -> cob.ldc(cob.constantPool().intEntry(value as Int))
          "java.lang.Float" -> cob.ldc(cob.constantPool().floatEntry(value as Float))
          "java.lang.Long" -> cob.ldc(cob.constantPool().longEntry(value as Long))
          "java.lang.Double" -> cob.ldc(cob.constantPool().doubleEntry(value as Double))
          else -> cob.ldc((value as ConstantDesc).remap())
        }
      }
      is RuntimeVisibleTypeAnnotationsAttribute ->
        cob.with(RuntimeVisibleTypeAnnotationsAttribute.of(coe.annotations().map { it.remap() }))
      is RuntimeInvisibleTypeAnnotationsAttribute ->
        cob.with(RuntimeInvisibleTypeAnnotationsAttribute.of(coe.annotations().map { it.remap() }))
      else -> cob.with(coe)
    }
  }

  private fun MethodTypeDesc.remap(): MethodTypeDesc {
    return MethodTypeDesc.of(
      mapClassDesc(returnType()),
      *parameterList().map { mapClassDesc(it) }.toTypedArray(),
    )
  }

  private fun ClassSignature.remap(): ClassSignature {
    val superclassSignature = superclassSignature()?.remap()
    return ClassSignature.of(
      typeParameters().map { it.remap() },
      superclassSignature,
      *superinterfaceSignatures().map { it.remap() }.toTypedArray(),
    )
  }

  private fun MethodSignature.remap(): MethodSignature {
    return MethodSignature.of(
      typeParameters().map { it.remap() },
      throwableSignatures().map { it.remap() },
      result().remap(),
      *arguments().map { it.remap() }.toTypedArray(),
    )
  }

  private fun RecordComponentInfo.remap(): RecordComponentInfo {
    return RecordComponentInfo.of(
      name().stringValue(),
      mapClassDesc(descriptorSymbol()),
      attributes().map { atr ->
        when (atr) {
          is SignatureAttribute -> SignatureAttribute.of(atr.asTypeSignature().remap())
          is RuntimeVisibleAnnotationsAttribute ->
            RuntimeVisibleAnnotationsAttribute.of(atr.annotations().map { it.remap() })
          is RuntimeInvisibleAnnotationsAttribute ->
            RuntimeInvisibleAnnotationsAttribute.of(atr.annotations().map { it.remap() })
          is RuntimeVisibleTypeAnnotationsAttribute ->
            RuntimeVisibleTypeAnnotationsAttribute.of(atr.annotations().map { it.remap() })
          is RuntimeInvisibleTypeAnnotationsAttribute ->
            RuntimeInvisibleTypeAnnotationsAttribute.of(atr.annotations().map { it.remap() })
          else -> atr
        }
      },
    )
  }

  private fun DirectMethodHandleDesc.remap(): DirectMethodHandleDesc {
    return when (kind()) {
      DirectMethodHandleDesc.Kind.GETTER,
      DirectMethodHandleDesc.Kind.SETTER,
      DirectMethodHandleDesc.Kind.STATIC_GETTER,
      DirectMethodHandleDesc.Kind.STATIC_SETTER ->
        MethodHandleDesc.ofField(
          kind(),
          mapClassDesc(owner()),
          methodName(),
          mapClassDesc(ClassDesc.ofDescriptor(lookupDescriptor())),
        )
      else ->
        MethodHandleDesc.ofMethod(
          kind(),
          mapClassDesc(owner()),
          methodName(),
          MethodTypeDesc.ofDescriptor(lookupDescriptor()).remap(),
        )
    }
  }

  private fun ConstantDesc.remap(): ConstantDesc {
    return when (this) {
      is ClassDesc -> mapClassDesc(this)
      is DynamicConstantDesc<*> -> remap()
      is DirectMethodHandleDesc -> remap()
      is MethodTypeDesc -> remap()
      else -> {
        @Suppress("CAST_NEVER_SUCCEEDS")
        if (javaClass.name == "java.lang.String") {
          map(toString(), mapLiterals = true) as ConstantDesc
        } else {
          this
        }
      }
    }
  }

  private fun DynamicConstantDesc<*>.remap(): DynamicConstantDesc<*> {
    return DynamicConstantDesc.ofNamed<Any>(
      bootstrapMethod().remap(),
      constantName(),
      mapClassDesc(constantType()),
      *bootstrapArgsList().map { it.remap() }.toTypedArray(),
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun <S : Signature> S.remap(): S {
    return when (this) {
      is Signature.ArrayTypeSig -> Signature.ArrayTypeSig.of(componentSignature().remap()) as S
      is Signature.ClassTypeSig -> {
        val mappedOuter = outerType().getOrNull()?.remap()
        val mappedClass = mapClassDesc(classDesc())
        // Extract internal name simply bypassing util
        val internalName = mappedClass.descriptorString().let { it.substring(1, it.length - 1) }
        Signature.ClassTypeSig.of(
          mappedOuter,
          internalName,
          *typeArgs()
            .map { ta ->
              when (ta) {
                is Signature.TypeArg.Unbounded -> ta
                is Signature.TypeArg.Bounded ->
                  Signature.TypeArg.bounded(ta.wildcardIndicator(), ta.boundType().remap())
              }
            }
            .toTypedArray(),
        ) as S
      }
      else -> this
    }
  }

  private fun Annotation.remap() =
    Annotation.of(
      mapClassDesc(classSymbol()),
      elements().map { el -> AnnotationElement.of(el.name(), el.value().remap()) },
    )

  private fun AnnotationValue.remap(): AnnotationValue {
    return when (this) {
      is AnnotationValue.OfAnnotation -> AnnotationValue.ofAnnotation(annotation().remap())
      is AnnotationValue.OfArray -> AnnotationValue.ofArray(values().map { it.remap() })
      is AnnotationValue.OfConstant -> {
        if (this is AnnotationValue.OfString) {
          val str = stringValue()
          // mapLiterals=true enables the skipStringConstants check in each relocator.
          val mapped = map(str, mapLiterals = true)
          if (mapped != str) AnnotationValue.ofString(mapped) else this
        } else {
          this
        }
      }
      is AnnotationValue.OfClass -> AnnotationValue.ofClass(mapClassDesc(classSymbol()))
      is AnnotationValue.OfEnum ->
        AnnotationValue.ofEnum(mapClassDesc(classSymbol()), constantName().stringValue())
    }
  }

  private fun TypeAnnotation.remap() =
    TypeAnnotation.of(targetInfo(), targetPath(), annotation().remap())

  private fun Signature.TypeParam.remap() =
    Signature.TypeParam.of(
      identifier(),
      classBound().getOrNull()?.remap(),
      *interfaceBounds().map { it.remap() }.toTypedArray(),
    )

  private fun map(name: String, mapLiterals: Boolean = false): String =
    relocators.mapName(name = name, mapLiterals = mapLiterals, onModified = onModified)
}
