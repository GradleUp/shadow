package com.github.jengelman.gradle.plugins.shadow.internal;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Interfaces;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Opcode;
import java.lang.classfile.Signature;
import java.lang.classfile.Superclass;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClassFileHelper {

  public static byte[] remapClass(byte[] classBytes, Function<ClassDesc, ClassDesc> mapFunction,
      Function<String, String> mapValueFunction) {
    ClassFile cc = ClassFile.of();
    ClassModel classModel = cc.parse(classBytes);
    return cc.transformClass(classModel, (ClassTransform) new SimpleClassRemapper(mapFunction, mapValueFunction));
  }

  private static class SimpleClassRemapper implements ClassTransform {
    private final Function<ClassDesc, ClassDesc> mapFunction;
    private final Function<String, String> mapValueFunction;

    public SimpleClassRemapper(Function<ClassDesc, ClassDesc> mapFunction, Function<String, String> mapValueFunction) {
      this.mapFunction = mapFunction;
      this.mapValueFunction = mapValueFunction;
    }

    @Override
    public void accept(ClassBuilder builder, ClassElement element) {
      if (element instanceof FieldModel fm) {
        // Remap field descriptor
        ClassDesc oldDesc = fm.fieldTypeSymbol();
        ClassDesc newDesc = mapFunction.apply(oldDesc);
        builder.withField(fm.fieldName().stringValue(), newDesc, fb -> {
          fb.withFlags(fm.flags().flagsMask());
          for (var elem : fm) {
            if (elem instanceof SignatureAttribute sa) {
              // Field Signature -> TypeSignature
              fb.with(SignatureAttribute
                  .of(remapSignature(Signature.parseFrom(sa.signature().stringValue()), mapFunction)));
            } else {
              fb.with(elem);
            }
          }
        });
      } else if (element instanceof MethodModel mm) {
        // Remap method descriptor
        MethodTypeDesc oldDesc = mm.methodTypeSymbol();
        MethodTypeDesc newDesc = mapMethodDesc(oldDesc, mapFunction);
        builder.withMethod(mm.methodName().stringValue(), newDesc, mm.flags().flagsMask(), mb -> {
          for (var elem : mm) {
            if (elem instanceof CodeAttribute code) {
              mb.transformCode(code, new SimpleCodeRemapper(mapFunction, mapValueFunction));
            } else if (elem instanceof SignatureAttribute sa) {
              // Method Signature -> MethodSignature
              try {
                MethodSignature ms = MethodSignature.parseFrom(sa.signature().stringValue());
                mb.with(SignatureAttribute.of(remapMethodSignature(ms, mapFunction)));
              } catch (Exception e) {
                e.printStackTrace();
                // Fallback or rethrow? Usually parseFrom shouldn't fail on valid class.
                // If it's not a MethodSignature (e.g. malformed), copy original
                mb.with(elem);
              }
            } else {
              mb.with(elem);
            }
          }
        });
      } else if (element instanceof Superclass sc) {
        // Remap superclass
        ClassDesc newSc = mapFunction.apply(sc.superclassEntry().asSymbol());
        builder.withSuperclass(builder.constantPool().classEntry(newSc));
      } else if (element instanceof Interfaces ifaces) {
        List<ClassEntry> newInterfaces = new ArrayList<>();
        for (ClassEntry iface : ifaces.interfaces()) {
          newInterfaces.add(builder.constantPool().classEntry(mapFunction.apply(iface.asSymbol())));
        }
        builder.withInterfaces(newInterfaces);
      } else if (element instanceof SignatureAttribute sa) {
        // Class Signature
        try {
          ClassSignature cs = ClassSignature.parseFrom(sa.signature().stringValue());
          builder.with(SignatureAttribute.of(remapClassSignature(cs, mapFunction)));
        } catch (Exception e) {
          e.printStackTrace();
          builder.with(element);
        }
      } else {
        builder.with(element);
      }
    }
  }

  private static class SimpleCodeRemapper implements CodeTransform {
    private final Function<ClassDesc, ClassDesc> mapFunction;
    private final Function<String, String> mapValueFunction;

    public SimpleCodeRemapper(Function<ClassDesc, ClassDesc> mapFunction, Function<String, String> mapValueFunction) {
      this.mapFunction = mapFunction;
      this.mapValueFunction = mapValueFunction;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
      if (element instanceof TypeCheckInstruction tci) { // CHECKCAST, INSTANCEOF
        ClassDesc newType = mapFunction.apply(tci.type().asSymbol());
        if (tci.opcode() == Opcode.CHECKCAST) {
          builder.checkcast(newType);
        } else if (tci.opcode() == Opcode.INSTANCEOF) {
          builder.instanceOf(newType);
        } else {
          builder.with(element); // fallback
        }
      } else if (element instanceof NewObjectInstruction noi) {
        builder.new_(mapFunction.apply(noi.className().asSymbol()));
      } else if (element instanceof NewReferenceArrayInstruction nrai) {
        builder.anewarray(mapFunction.apply(nrai.componentType().asSymbol()));
      } else if (element instanceof FieldInstruction fi) {
        ClassDesc newOwner = mapFunction.apply(fi.owner().asSymbol());
        ClassDesc newType = mapFunction.apply(fi.typeSymbol());
        String name = fi.name().stringValue();
        Opcode op = fi.opcode();
        if (op == Opcode.GETFIELD)
          builder.getfield(newOwner, name, newType);
        else if (op == Opcode.PUTFIELD)
          builder.putfield(newOwner, name, newType);
        else if (op == Opcode.GETSTATIC)
          builder.getstatic(newOwner, name, newType);
        else if (op == Opcode.PUTSTATIC)
          builder.putstatic(newOwner, name, newType);
        else
          builder.with(element);
      } else if (element instanceof InvokeInstruction ii) {
        ClassDesc newOwner = mapFunction.apply(ii.owner().asSymbol());
        MethodTypeDesc newDesc = mapMethodDesc(ii.typeSymbol(), mapFunction);
        String name = ii.name().stringValue();
        boolean isInterface = ii.isInterface();
        Opcode op = ii.opcode();
        if (op == Opcode.INVOKEVIRTUAL)
          builder.invokevirtual(newOwner, name, newDesc);
        else if (op == Opcode.INVOKESPECIAL)
          builder.invokespecial(newOwner, name, newDesc, isInterface);
        else if (op == Opcode.INVOKESTATIC)
          builder.invokestatic(newOwner, name, newDesc, isInterface);
        else if (op == Opcode.INVOKEINTERFACE)
          builder.invokeinterface(newOwner, name, newDesc);
        else
          builder.with(element);
      } else if (element instanceof ConstantInstruction.LoadConstantInstruction lci) {
        if (lci.constantEntry() instanceof ClassEntry ce) {
          builder.ldc(mapFunction.apply(ce.asSymbol()));
        } else if (lci.constantEntry() instanceof StringEntry se) {
          builder.ldc(mapValueFunction.apply(se.stringValue()));
        } else {
          builder.with(element);
        }
      } else {
        builder.with(element);
      }
    }
  }

  private static MethodTypeDesc mapMethodDesc(MethodTypeDesc desc, Function<ClassDesc, ClassDesc> mapFunction) {
    ClassDesc newReturnType = mapFunction.apply(desc.returnType());
    ClassDesc[] newParamTypes = desc.parameterList().stream().map(mapFunction).toArray(ClassDesc[]::new);
    return MethodTypeDesc.of(newReturnType, newParamTypes);
  }

  private static ClassSignature remapClassSignature(ClassSignature cs, Function<ClassDesc, ClassDesc> mapFunction) {
    return ClassSignature.of(
        remapTypeParams(cs.typeParameters(), mapFunction), // Expecting List
        (Signature.ClassTypeSig) remapSignature(cs.superclassSignature(), mapFunction),
        cs.superinterfaceSignatures().stream().map(s -> (Signature.ClassTypeSig) remapSignature(s, mapFunction))
            .toArray(Signature.ClassTypeSig[]::new));
  }

  private static MethodSignature remapMethodSignature(MethodSignature ms, Function<ClassDesc, ClassDesc> mapFunction) {
    return MethodSignature.of(
        remapTypeParams(ms.typeParameters(), mapFunction), // Expecting List
        ms.throwableSignatures().stream().map(s -> (Signature.ThrowableSig) remapSignature(s, mapFunction))
            .collect(Collectors.toList()),
        remapSignature(ms.result(), mapFunction),
        ms.arguments().stream().map(s -> remapSignature(s, mapFunction)).toArray(Signature[]::new));
  }

  private static Signature remapSignature(Signature sig, Function<ClassDesc, ClassDesc> mapFunction) {
    System.err.println("DEBUG: remapSignature " + sig.getClass().getSimpleName());
    if (sig instanceof Signature.ClassTypeSig cts) {
      ClassDesc newDesc = mapFunction.apply(cts.classDesc());
      List<Signature.TypeArg> newArgs = new ArrayList<>();
      for (Signature.TypeArg arg : cts.typeArgs()) {
        if (arg instanceof Signature.TypeArg.Bounded boundedArg) {
          Signature.RefTypeSig newBound = (Signature.RefTypeSig) remapSignature(boundedArg.boundType(), mapFunction);
          Signature.TypeArg.Bounded.WildcardIndicator indicator = boundedArg.wildcardIndicator();
          if (indicator == Signature.TypeArg.Bounded.WildcardIndicator.NONE) {
            newArgs.add(Signature.TypeArg.of(newBound));
          } else if (indicator == Signature.TypeArg.Bounded.WildcardIndicator.EXTENDS) {
            newArgs.add(Signature.TypeArg.extendsOf(newBound));
          } else if (indicator == Signature.TypeArg.Bounded.WildcardIndicator.SUPER) {
            newArgs.add(Signature.TypeArg.superOf(newBound));
          }
        } else if (arg instanceof Signature.TypeArg.Unbounded) {
          newArgs.add(arg);
        }
      }
      Signature.ClassTypeSig outer = cts.outerType().orElse(null);
      Signature.ClassTypeSig newOuter = outer != null ? (Signature.ClassTypeSig) remapSignature(outer, mapFunction)
          : null;
      if (newOuter != null) {
        return Signature.ClassTypeSig.of(newOuter, newDesc, newArgs.toArray(Signature.TypeArg[]::new));
      } else {
        return Signature.ClassTypeSig.of(newDesc, newArgs.toArray(Signature.TypeArg[]::new));
      }
    } else if (sig instanceof Signature.ArrayTypeSig ats) {
      return Signature.ArrayTypeSig.of(remapSignature(ats.componentSignature(), mapFunction));
    } else if (sig instanceof Signature.TypeVarSig) {
      return sig; // Keep type var name
    } else if (sig instanceof Signature.BaseTypeSig) {
      return sig;
    }
    return sig;
  }

  private static List<Signature.TypeParam> remapTypeParams(List<Signature.TypeParam> params,
      Function<ClassDesc, ClassDesc> mapFunction) {
    List<Signature.TypeParam> newParams = new ArrayList<>();
    for (Signature.TypeParam p : params) {
      Signature.RefTypeSig newClassBound = p.classBound().isPresent()
          ? (Signature.RefTypeSig) remapSignature(p.classBound().get(), mapFunction)
          : null;
      List<Signature.RefTypeSig> newInterfaceBounds = p.interfaceBounds().stream()
          .map(s -> (Signature.RefTypeSig) remapSignature(s, mapFunction)).collect(Collectors.toList());
      newParams.add(Signature.TypeParam.of(p.identifier(), newClassBound,
          newInterfaceBounds.toArray(Signature.RefTypeSig[]::new)));
    }
    return newParams;
  }
}
