package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import java.lang.classfile.AnnotationElement
import java.lang.classfile.AnnotationValue
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
import java.util.regex.Pattern

/**
 * Ported from ASM's Remapper and JDK's ClassRemapperImpl to use Java 24 Class-File API.
 *
 * @author John Engelman
 * @author Ported for Class-File API
 */
internal class RelocatorRemapper(
  private val relocators: Set<Relocator>,
  private val onModified: () -> Unit = {},
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
          mapMethodDesc(cle.methodTypeSymbol()),
          cle.flags().flagsMask(),
        ) { mb ->
          mb.transform(cle, asMethodTransform())
        }
      is Superclass -> clb.withSuperclass(mapClassDesc(cle.superclassEntry().asSymbol()))
      is Interfaces ->
        clb.withInterfaceSymbols(cle.interfaces().map { mapClassDesc(it.asSymbol())!! })
      is SignatureAttribute ->
        clb.with(SignatureAttribute.of(mapClassSignature(cle.asClassSignature())))
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
              .map(this::mapMethodDesc),
          )
        )
      is RecordAttribute ->
        clb.with(RecordAttribute.of(cle.components().map(this::mapRecordComponent)))
      is ModuleAttribute ->
        clb.with(
          ModuleAttribute.of(
            cle.moduleName(),
            cle.moduleFlagsMask(),
            cle.moduleVersion().orElse(null),
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
            cle.uses().map { clb.constantPool().classEntry(mapClassDesc(it.asSymbol())!!) },
            cle.provides().map { mp ->
              ModuleProvideInfo.of(
                mapClassDesc(mp.provides().asSymbol()),
                mp.providesWith().map { mapClassDesc(it.asSymbol())!! },
              )
            },
          )
        )
      is NestHostAttribute ->
        clb.with(NestHostAttribute.of(mapClassDesc(cle.nestHost().asSymbol())))
      is NestMembersAttribute ->
        clb.with(
          NestMembersAttribute.ofSymbols(cle.nestMembers().map { mapClassDesc(it.asSymbol())!! })
        )
      is PermittedSubclassesAttribute ->
        clb.with(
          PermittedSubclassesAttribute.ofSymbols(
            cle.permittedSubclasses().map { mapClassDesc(it.asSymbol())!! }
          )
        )
      is RuntimeVisibleAnnotationsAttribute ->
        clb.with(RuntimeVisibleAnnotationsAttribute.of(mapAnnotations(cle.annotations())))
      is RuntimeInvisibleAnnotationsAttribute ->
        clb.with(RuntimeInvisibleAnnotationsAttribute.of(mapAnnotations(cle.annotations())))
      is RuntimeVisibleTypeAnnotationsAttribute ->
        clb.with(RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(cle.annotations())))
      is RuntimeInvisibleTypeAnnotationsAttribute ->
        clb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(cle.annotations())))
      else -> clb.with(cle)
    }
  }

  private fun asFieldTransform(): FieldTransform = FieldTransform { fb, fe ->
    when (fe) {
      is SignatureAttribute -> fb.with(SignatureAttribute.of(mapSignature(fe.asTypeSignature())))
      is RuntimeVisibleAnnotationsAttribute ->
        fb.with(RuntimeVisibleAnnotationsAttribute.of(mapAnnotations(fe.annotations())))
      is RuntimeInvisibleAnnotationsAttribute ->
        fb.with(RuntimeInvisibleAnnotationsAttribute.of(mapAnnotations(fe.annotations())))
      is RuntimeVisibleTypeAnnotationsAttribute ->
        fb.with(RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(fe.annotations())))
      is RuntimeInvisibleTypeAnnotationsAttribute ->
        fb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(fe.annotations())))
      else -> fb.with(fe)
    }
  }

  private fun asMethodTransform(): MethodTransform = MethodTransform { mb, me ->
    when (me) {
      is AnnotationDefaultAttribute ->
        mb.with(AnnotationDefaultAttribute.of(mapAnnotationValue(me.defaultValue())))
      is CodeModel -> mb.transformCode(me, asCodeTransform())
      is ExceptionsAttribute ->
        mb.with(
          ExceptionsAttribute.ofSymbols(me.exceptions().map { mapClassDesc(it.asSymbol())!! })
        )
      is SignatureAttribute ->
        mb.with(SignatureAttribute.of(mapMethodSignature(me.asMethodSignature())))
      is RuntimeVisibleAnnotationsAttribute ->
        mb.with(RuntimeVisibleAnnotationsAttribute.of(mapAnnotations(me.annotations())))
      is RuntimeInvisibleAnnotationsAttribute ->
        mb.with(RuntimeInvisibleAnnotationsAttribute.of(mapAnnotations(me.annotations())))
      is RuntimeVisibleParameterAnnotationsAttribute ->
        mb.with(
          RuntimeVisibleParameterAnnotationsAttribute.of(
            me.parameterAnnotations().map(this::mapAnnotations)
          )
        )
      is RuntimeInvisibleParameterAnnotationsAttribute ->
        mb.with(
          RuntimeInvisibleParameterAnnotationsAttribute.of(
            me.parameterAnnotations().map(this::mapAnnotations)
          )
        )
      is RuntimeVisibleTypeAnnotationsAttribute ->
        mb.with(RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(me.annotations())))
      is RuntimeInvisibleTypeAnnotationsAttribute ->
        mb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(me.annotations())))
      else -> mb.with(me)
    }
  }

  private fun asCodeTransform(): CodeTransform = CodeTransform { cob, coe ->
    when (coe) {
      is FieldInstruction ->
        cob.fieldAccess(
          coe.opcode(),
          mapClassDesc(coe.owner().asSymbol())!!,
          coe.name().stringValue(),
          mapClassDesc(coe.typeSymbol())!!,
        )
      is InvokeInstruction ->
        cob.invoke(
          coe.opcode(),
          mapClassDesc(coe.owner().asSymbol())!!,
          coe.name().stringValue(),
          mapMethodDesc(coe.typeSymbol()),
          coe.isInterface(),
        )
      is InvokeDynamicInstruction ->
        cob.invokedynamic(
          DynamicCallSiteDesc.of(
            mapDirectMethodHandle(coe.bootstrapMethod()),
            coe.name().stringValue(),
            mapMethodDesc(coe.typeSymbol()),
            *coe.bootstrapArgs().map(this::mapConstantValue).toTypedArray(),
          )
        )
      is NewObjectInstruction -> cob.new_(mapClassDesc(coe.className().asSymbol())!!)
      is NewReferenceArrayInstruction ->
        cob.anewarray(mapClassDesc(coe.componentType().asSymbol())!!)
      is NewMultiArrayInstruction ->
        cob.multianewarray(mapClassDesc(coe.arrayType().asSymbol())!!, coe.dimensions())
      is TypeCheckInstruction ->
        cob.with(TypeCheckInstruction.of(coe.opcode(), mapClassDesc(coe.type().asSymbol())!!))
      is ExceptionCatch ->
        cob.exceptionCatch(
          coe.tryStart(),
          coe.tryEnd(),
          coe.handler(),
          coe.catchType().map { cob.constantPool().classEntry(mapClassDesc(it.asSymbol())!!) },
        )
      is LocalVariable ->
        cob.localVariable(
          coe.slot(),
          coe.name().stringValue(),
          mapClassDesc(coe.typeSymbol())!!,
          coe.startScope(),
          coe.endScope(),
        )
      is LocalVariableType ->
        cob.localVariableType(
          coe.slot(),
          coe.name().stringValue(),
          mapSignature(coe.signatureSymbol()),
          coe.startScope(),
          coe.endScope(),
        )
      is ConstantInstruction.LoadConstantInstruction -> {
        val value = coe.constantEntry().constantValue()
        val name = value.javaClass.name
        if (name == "java.lang.String") {
          val s = value.toString()
          cob.ldc(cob.constantPool().stringEntry(map(s, mapLiterals = true)))
        } else if (name == "java.lang.Integer") {
          cob.ldc(cob.constantPool().intEntry(value as Int))
        } else if (name == "java.lang.Float") {
          cob.ldc(cob.constantPool().floatEntry(value as Float))
        } else if (name == "java.lang.Long") {
          cob.ldc(cob.constantPool().longEntry(value as Long))
        } else if (name == "java.lang.Double") {
          cob.ldc(cob.constantPool().doubleEntry(value as Double))
        } else {
          cob.ldc(mapConstantValue(value as ConstantDesc))
        }
      }
      is RuntimeVisibleTypeAnnotationsAttribute ->
        cob.with(RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(coe.annotations())))
      is RuntimeInvisibleTypeAnnotationsAttribute ->
        cob.with(RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(coe.annotations())))
      else -> cob.with(coe)
    }
  }

  fun mapClassDesc(desc: ClassDesc?): ClassDesc? {
    if (desc == null) return null
    if (desc.isArray) return mapClassDesc(desc.componentType())!!.arrayType()
    if (desc.isPrimitive) return desc
    val internalName = desc.descriptorString().let { it.substring(1, it.length - 1) }
    val mappedInternalName = map(internalName)
    return if (internalName == mappedInternalName) desc
    else ClassDesc.ofDescriptor("L$mappedInternalName;")
  }

  private fun mapMethodDesc(desc: MethodTypeDesc): MethodTypeDesc {
    return MethodTypeDesc.of(
      mapClassDesc(desc.returnType()),
      *desc.parameterList().map { mapClassDesc(it)!! }.toTypedArray(),
    )
  }

  private fun mapClassSignature(signature: ClassSignature): ClassSignature {
    val superclassSignature = signature.superclassSignature()?.let { mapSignature(it) }
    return ClassSignature.of(
      mapTypeParams(signature.typeParameters()),
      superclassSignature,
      *signature.superinterfaceSignatures().map { mapSignature(it) }.toTypedArray(),
    )
  }

  private fun mapMethodSignature(signature: MethodSignature): MethodSignature {
    return MethodSignature.of(
      mapTypeParams(signature.typeParameters()),
      signature.throwableSignatures().map { mapSignature(it) },
      mapSignature(signature.result()),
      *signature.arguments().map { mapSignature(it) }.toTypedArray(),
    )
  }

  private fun mapRecordComponent(component: RecordComponentInfo): RecordComponentInfo {
    return RecordComponentInfo.of(
      component.name().stringValue(),
      mapClassDesc(component.descriptorSymbol())!!,
      component.attributes().map { atr ->
        when (atr) {
          is SignatureAttribute -> SignatureAttribute.of(mapSignature(atr.asTypeSignature()))
          is RuntimeVisibleAnnotationsAttribute ->
            RuntimeVisibleAnnotationsAttribute.of(mapAnnotations(atr.annotations()))
          is RuntimeInvisibleAnnotationsAttribute ->
            RuntimeInvisibleAnnotationsAttribute.of(mapAnnotations(atr.annotations()))
          is RuntimeVisibleTypeAnnotationsAttribute ->
            RuntimeVisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(atr.annotations()))
          is RuntimeInvisibleTypeAnnotationsAttribute ->
            RuntimeInvisibleTypeAnnotationsAttribute.of(mapTypeAnnotations(atr.annotations()))
          else -> atr
        }
      },
    )
  }

  private fun mapDirectMethodHandle(dmhd: DirectMethodHandleDesc): DirectMethodHandleDesc {
    return when (dmhd.kind()) {
      DirectMethodHandleDesc.Kind.GETTER,
      DirectMethodHandleDesc.Kind.SETTER,
      DirectMethodHandleDesc.Kind.STATIC_GETTER,
      DirectMethodHandleDesc.Kind.STATIC_SETTER ->
        MethodHandleDesc.ofField(
          dmhd.kind(),
          mapClassDesc(dmhd.owner())!!,
          dmhd.methodName(),
          mapClassDesc(ClassDesc.ofDescriptor(dmhd.lookupDescriptor()))!!,
        )
      else ->
        MethodHandleDesc.ofMethod(
          dmhd.kind(),
          mapClassDesc(dmhd.owner())!!,
          dmhd.methodName(),
          mapMethodDesc(MethodTypeDesc.ofDescriptor(dmhd.lookupDescriptor())),
        )
    }
  }

  private fun mapConstantValue(value: ConstantDesc): ConstantDesc {
    return when (value) {
      is ClassDesc -> mapClassDesc(value)!!
      is DynamicConstantDesc<*> -> mapDynamicConstant(value)
      is DirectMethodHandleDesc -> mapDirectMethodHandle(value)
      is MethodTypeDesc -> mapMethodDesc(value)
      else -> {
        if (value.javaClass.name == "java.lang.String")
          map(value.toString(), mapLiterals = true) as ConstantDesc
        else value
      }
    }
  }

  private fun mapDynamicConstant(dcd: DynamicConstantDesc<*>): DynamicConstantDesc<*> {
    return DynamicConstantDesc.ofNamed<Any>(
      mapDirectMethodHandle(dcd.bootstrapMethod()),
      dcd.constantName(),
      mapClassDesc(dcd.constantType())!!,
      *dcd.bootstrapArgsList().map(this::mapConstantValue).toTypedArray(),
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun <S : Signature> mapSignature(signature: S): S {
    return when (signature) {
      is Signature.ArrayTypeSig ->
        Signature.ArrayTypeSig.of(mapSignature(signature.componentSignature())) as S
      is Signature.ClassTypeSig -> {
        val mappedOuter = signature.outerType().orElse(null)?.let { mapSignature(it) }
        val mappedClass = mapClassDesc(signature.classDesc())!!
        // Extract internal name simply bypassing util
        val internalName = mappedClass.descriptorString().let { it.substring(1, it.length - 1) }
        Signature.ClassTypeSig.of(
          mappedOuter,
          internalName,
          *signature
            .typeArgs()
            .map { ta ->
              when (ta) {
                is Signature.TypeArg.Unbounded -> ta
                is Signature.TypeArg.Bounded ->
                  Signature.TypeArg.bounded(ta.wildcardIndicator(), mapSignature(ta.boundType()))
              }
            }
            .toTypedArray(),
        ) as S
      }
      else -> signature
    }
  }

  private fun mapAnnotations(
    annotations: List<java.lang.classfile.Annotation>
  ): List<java.lang.classfile.Annotation> = annotations.map(this::mapAnnotation)

  private fun mapAnnotation(a: java.lang.classfile.Annotation): java.lang.classfile.Annotation =
    java.lang.classfile.Annotation.of(
      mapClassDesc(a.classSymbol())!!,
      a.elements().map { el -> AnnotationElement.of(el.name(), mapAnnotationValue(el.value())) },
    )

  private fun mapAnnotationValue(valObj: AnnotationValue): AnnotationValue {
    return when (valObj) {
      is AnnotationValue.OfAnnotation ->
        AnnotationValue.ofAnnotation(mapAnnotation(valObj.annotation()))
      is AnnotationValue.OfArray ->
        AnnotationValue.ofArray(valObj.values().map(this::mapAnnotationValue))
      is AnnotationValue.OfConstant -> {
        if (valObj is AnnotationValue.OfString) {
          val str = valObj.stringValue()
          // mapLiterals=true enables the skipStringConstants check in each relocator.
          val mapped = map(str, mapLiterals = true)
          if (mapped != str) AnnotationValue.ofString(mapped) else valObj
        } else {
          valObj
        }
      }
      is AnnotationValue.OfClass -> AnnotationValue.ofClass(mapClassDesc(valObj.classSymbol())!!)
      is AnnotationValue.OfEnum ->
        AnnotationValue.ofEnum(
          mapClassDesc(valObj.classSymbol())!!,
          valObj.constantName().stringValue(),
        )
    }
  }

  private fun mapTypeAnnotations(typeAnnotations: List<TypeAnnotation>): List<TypeAnnotation> =
    typeAnnotations.map { a ->
      TypeAnnotation.of(a.targetInfo(), a.targetPath(), mapAnnotation(a.annotation()))
    }

  private fun mapTypeParams(typeParams: List<Signature.TypeParam>): List<Signature.TypeParam> =
    typeParams.map { tp ->
      Signature.TypeParam.of(
        tp.identifier(),
        tp.classBound().orElse(null)?.let { mapSignature(it) },
        *tp.interfaceBounds().map { mapSignature(it) }.toTypedArray(),
      )
    }

  fun map(name: String): String = map(name, false)

  fun map(name: String, mapLiterals: Boolean = false): String {
    // Maybe a list of types.
    val newName = name.split(';').joinToString(";") { mapNameImpl(it, mapLiterals) }

    if (newName != name) {
      onModified()
    }
    return newName
  }

  private fun mapNameImpl(name: String, mapLiterals: Boolean): String {
    var newName = name
    var prefix = ""
    var suffix = ""

    val matcher = classPattern.matcher(newName)
    if (matcher.matches()) {
      prefix = matcher.group(1) + "L"
      suffix = ""
      newName = matcher.group(2)
    }

    for (relocator in relocators) {
      if (mapLiterals && relocator.skipStringConstants) {
        return name
      } else if (relocator.canRelocateClass(newName)) {
        return prefix + relocator.relocateClass(newName) + suffix
      } else if (relocator.canRelocatePath(newName)) {
        return prefix + relocator.relocatePath(newName) + suffix
      }
    }

    return name
  }

  private companion object {
    /** https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html */
    val classPattern: Pattern = Pattern.compile("([\\[()BCDFIJSZ]*)?L([^;]+);?")
  }
}
