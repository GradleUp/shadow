package com.github.jengelman.gradle.plugins.shadow.internal

import java.lang.classfile.ClassBuilder
import java.lang.classfile.ClassElement
import java.lang.classfile.ClassFile
import java.lang.classfile.ClassSignature
import java.lang.classfile.ClassTransform
import java.lang.classfile.CodeBuilder
import java.lang.classfile.CodeElement
import java.lang.classfile.CodeTransform
import java.lang.classfile.FieldModel
import java.lang.classfile.Interfaces
import java.lang.classfile.MethodModel
import java.lang.classfile.MethodSignature
import java.lang.classfile.Opcode
import java.lang.classfile.Signature
import java.lang.classfile.Superclass
import java.lang.classfile.attribute.CodeAttribute
import java.lang.classfile.attribute.SignatureAttribute
import java.lang.classfile.constantpool.ClassEntry
import java.lang.classfile.constantpool.StringEntry
import java.lang.classfile.instruction.ConstantInstruction
import java.lang.classfile.instruction.FieldInstruction
import java.lang.classfile.instruction.InvokeInstruction
import java.lang.classfile.instruction.NewObjectInstruction
import java.lang.classfile.instruction.NewReferenceArrayInstruction
import java.lang.classfile.instruction.TypeCheckInstruction
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDesc
import java.lang.constant.MethodTypeDesc
import java.util.function.Function

internal object ClassFileHelper {

  fun remapClass(
    classBytes: ByteArray,
    mapFunction: Function<ClassDesc, ClassDesc>,
    mapValueFunction: Function<String, String>,
  ): ByteArray {
    val cc = ClassFile.of()
    val classModel = cc.parse(classBytes)
    return cc.transformClass(classModel, SimpleClassRemapper(mapFunction, mapValueFunction))
  }

  private class SimpleClassRemapper(
    private val mapFunction: Function<ClassDesc, ClassDesc>,
    private val mapValueFunction: Function<String, String>,
  ) : ClassTransform {
    override fun accept(builder: ClassBuilder, element: ClassElement) {
      when (element) {
        is FieldModel -> {
          // Remap field descriptor
          val oldDesc = element.fieldTypeSymbol()
          val newDesc = mapFunction.apply(oldDesc)
          builder.withField(element.fieldName().stringValue(), newDesc) { fb ->
            fb.withFlags(element.flags().flagsMask())
            for (elem in element) {
              if (elem is SignatureAttribute) {
                // Field Signature -> TypeSignature
                fb.with(
                  SignatureAttribute.of(
                    remapSignature(Signature.parseFrom(elem.signature().stringValue()), mapFunction)
                  )
                )
              } else {
                fb.with(elem)
              }
            }
          }
        }
        is MethodModel -> {
          // Remap method descriptor
          val oldDesc = element.methodTypeSymbol()
          val newDesc = mapMethodDesc(oldDesc, mapFunction)
          builder.withMethod(
            element.methodName().stringValue(),
            newDesc,
            element.flags().flagsMask(),
          ) { mb ->
            for (elem in element) {
              if (elem is CodeAttribute) {
                mb.transformCode(elem, SimpleCodeRemapper(mapFunction, mapValueFunction))
              } else if (elem is SignatureAttribute) {
                // Method Signature -> MethodSignature
                try {
                  val ms = MethodSignature.parseFrom(elem.signature().stringValue())
                  mb.with(SignatureAttribute.of(remapMethodSignature(ms, mapFunction)))
                } catch (e: Exception) {
                  e.printStackTrace()
                  // Fallback or rethrow? Usually parseFrom shouldn't fail on valid class.
                  // If it's not a MethodSignature (e.g. malformed), copy original
                  mb.with(elem)
                }
              } else {
                mb.with(elem)
              }
            }
          }
        }
        is Superclass -> {
          // Remap superclass
          val newSc = mapFunction.apply(element.superclassEntry().asSymbol())
          builder.withSuperclass(builder.constantPool().classEntry(newSc))
        }
        is Interfaces -> {
          val newInterfaces = ArrayList<ClassEntry>()
          for (iface in element.interfaces()) {
            newInterfaces.add(
              builder.constantPool().classEntry(mapFunction.apply(iface.asSymbol()))
            )
          }
          builder.withInterfaces(newInterfaces)
        }
        is SignatureAttribute -> {
          // Class Signature
          try {
            val cs = ClassSignature.parseFrom(element.signature().stringValue())
            builder.with(SignatureAttribute.of(remapClassSignature(cs, mapFunction)))
          } catch (e: Exception) {
            e.printStackTrace()
            builder.with(element)
          }
        }
        else -> {
          builder.with(element)
        }
      }
    }
  }

  private class SimpleCodeRemapper(
    private val mapFunction: Function<ClassDesc, ClassDesc>,
    private val mapValueFunction: Function<String, String>,
  ) : CodeTransform {

    override fun accept(builder: CodeBuilder, element: CodeElement) {
      when (element) {
        is TypeCheckInstruction -> { // CHECKCAST, INSTANCEOF
          val newType = mapFunction.apply(element.type().asSymbol())
          if (element.opcode() == Opcode.CHECKCAST) {
            builder.checkcast(newType)
          } else if (element.opcode() == Opcode.INSTANCEOF) {
            builder.instanceOf(newType)
          } else {
            builder.with(element) // fallback
          }
        }
        is NewObjectInstruction -> {
          builder.new_(mapFunction.apply(element.className().asSymbol()))
        }
        is NewReferenceArrayInstruction -> {
          builder.anewarray(mapFunction.apply(element.componentType().asSymbol()))
        }
        is FieldInstruction -> {
          val newOwner = mapFunction.apply(element.owner().asSymbol())
          val newType = mapFunction.apply(element.typeSymbol())
          val name = element.name().stringValue()
          val op = element.opcode()
          if (op == Opcode.GETFIELD) builder.getfield(newOwner, name, newType)
          else if (op == Opcode.PUTFIELD) builder.putfield(newOwner, name, newType)
          else if (op == Opcode.GETSTATIC) builder.getstatic(newOwner, name, newType)
          else if (op == Opcode.PUTSTATIC) builder.putstatic(newOwner, name, newType)
          else builder.with(element)
        }
        is InvokeInstruction -> {
          val newOwner = mapFunction.apply(element.owner().asSymbol())
          val newDesc = mapMethodDesc(element.typeSymbol(), mapFunction)
          val name = element.name().stringValue()
          val isInterface = element.isInterface()
          val op = element.opcode()
          if (op == Opcode.INVOKEVIRTUAL) builder.invokevirtual(newOwner, name, newDesc)
          else if (op == Opcode.INVOKESPECIAL)
            builder.invokespecial(newOwner, name, newDesc, isInterface)
          else if (op == Opcode.INVOKESTATIC)
            builder.invokestatic(newOwner, name, newDesc, isInterface)
          else if (op == Opcode.INVOKEINTERFACE) builder.invokeinterface(newOwner, name, newDesc)
          else builder.with(element)
        }
        is ConstantInstruction.LoadConstantInstruction -> {
          if (element.constantEntry() is ClassEntry) {
            val ce = element.constantEntry() as ClassEntry
            builder.ldc(mapFunction.apply(ce.asSymbol()))
          } else if (element.constantEntry() is StringEntry) {
            val se = element.constantEntry() as StringEntry
            builder.ldc(mapValueFunction.apply(se.stringValue()) as ConstantDesc)
          } else {
            builder.with(element)
          }
        }
        else -> {
          builder.with(element)
        }
      }
    }
  }

  private fun mapMethodDesc(
    desc: MethodTypeDesc,
    mapFunction: Function<ClassDesc, ClassDesc>,
  ): MethodTypeDesc {
    val newReturnType = mapFunction.apply(desc.returnType())
    val newParamTypes =
      desc.parameterList().stream().map(mapFunction).toArray { size ->
        arrayOfNulls<ClassDesc>(size)
      }
    return MethodTypeDesc.of(newReturnType, *newParamTypes)
  }

  private fun remapClassSignature(
    cs: ClassSignature,
    mapFunction: Function<ClassDesc, ClassDesc>,
  ): ClassSignature {
    return ClassSignature.of(
      remapTypeParams(cs.typeParameters(), mapFunction), // Expecting List
      remapSignature(cs.superclassSignature(), mapFunction) as Signature.ClassTypeSig,
      *cs
        .superinterfaceSignatures()
        .stream()
        .map { s -> remapSignature(s, mapFunction) as Signature.ClassTypeSig }
        .toArray { size -> arrayOfNulls<Signature.ClassTypeSig>(size) },
    )
  }

  private fun remapMethodSignature(
    ms: MethodSignature,
    mapFunction: Function<ClassDesc, ClassDesc>,
  ): MethodSignature {
    return MethodSignature.of(
      remapTypeParams(ms.typeParameters(), mapFunction), // Expecting List
      ms
        .throwableSignatures()
        .stream()
        .map { s -> remapSignature(s, mapFunction) as Signature.ThrowableSig }
        .toList(),
      remapSignature(ms.result(), mapFunction),
      *ms
        .arguments()
        .stream()
        .map { s -> remapSignature(s, mapFunction) }
        .toArray { size -> arrayOfNulls<Signature>(size) },
    )
  }

  private fun remapSignature(
    sig: Signature,
    mapFunction: Function<ClassDesc, ClassDesc>,
  ): Signature {
    System.err.println("DEBUG: remapSignature " + sig.javaClass.simpleName)
    if (sig is Signature.ClassTypeSig) {
      val newDesc = mapFunction.apply(sig.classDesc())
      val newArgs = ArrayList<Signature.TypeArg>()
      for (arg in sig.typeArgs()) {
        if (arg is Signature.TypeArg.Bounded) {
          val newBound = remapSignature(arg.boundType(), mapFunction) as Signature.RefTypeSig
          val indicator = arg.wildcardIndicator()
          if (indicator == Signature.TypeArg.Bounded.WildcardIndicator.NONE) {
            newArgs.add(Signature.TypeArg.of(newBound))
          } else if (indicator == Signature.TypeArg.Bounded.WildcardIndicator.EXTENDS) {
            newArgs.add(Signature.TypeArg.extendsOf(newBound))
          } else if (indicator == Signature.TypeArg.Bounded.WildcardIndicator.SUPER) {
            newArgs.add(Signature.TypeArg.superOf(newBound))
          }
        } else if (arg is Signature.TypeArg.Unbounded) {
          newArgs.add(arg)
        }
      }
      val outer = sig.outerType().orElse(null)
      val newOuter =
        if (outer != null) remapSignature(outer, mapFunction) as Signature.ClassTypeSig else null
      if (newOuter != null) {
        return Signature.ClassTypeSig.of(newOuter, newDesc, *newArgs.toTypedArray())
      } else {
        return Signature.ClassTypeSig.of(newDesc, *newArgs.toTypedArray())
      }
    } else if (sig is Signature.ArrayTypeSig) {
      return Signature.ArrayTypeSig.of(remapSignature(sig.componentSignature(), mapFunction))
    } else if (sig is Signature.TypeVarSig) {
      return sig // Keep type var name
    } else if (sig is Signature.BaseTypeSig) {
      return sig
    }
    return sig
  }

  private fun remapTypeParams(
    params: List<Signature.TypeParam>,
    mapFunction: Function<ClassDesc, ClassDesc>,
  ): List<Signature.TypeParam> {
    val newParams = ArrayList<Signature.TypeParam>()
    for (p in params) {
      val newClassBound =
        if (p.classBound().isPresent)
          remapSignature(p.classBound().get(), mapFunction) as Signature.RefTypeSig
        else null
      val newInterfaceBounds =
        p.interfaceBounds()
          .stream()
          .map { s -> remapSignature(s, mapFunction) as Signature.RefTypeSig }
          .toArray { size -> arrayOfNulls<Signature.RefTypeSig>(size) }
      newParams.add(Signature.TypeParam.of(p.identifier(), newClassBound, *newInterfaceBounds))
    }
    return newParams
  }
}
