package com.bazel.jdt;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;

public class JDTUtilsPatcher implements WeavingHook, Opcodes {

    private static final Logger LOG = Logger.getLogger(JDTUtilsPatcher.class.getName());

    static final String JDTUTILS_INTERNAL_NAME = "org/eclipse/jdt/ls/core/internal/JDTUtils";
    static final String TARGET_METHOD = "searchDecompiledSources";
    private static final String TARGET_BUNDLE = "org.eclipse.jdt.ls.core";
    private static final String NPE_INTERNAL_NAME = "java/lang/NullPointerException";
    private static final String COLLECTIONS_INTERNAL_NAME = "java/util/Collections";

    @Override
    public void weave(WovenClass wovenClass) {
        String bundleName = wovenClass.getBundleWiring().getBundle().getSymbolicName();
        if (!TARGET_BUNDLE.equals(bundleName)) {
            return;
        }

        byte[] original = wovenClass.getBytes();
        if (!containsTargetCallSite(original)) {
            return;
        }

        try {
            byte[] patched = patchCallerClass(original);
            if (patched != null) {
                wovenClass.setBytes(patched);
                LOG.info("Patched " + wovenClass.getClassName()
                        + ": wrapped searchDecompiledSources call site with NPE guard");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to patch " + wovenClass.getClassName() + ", leaving class unmodified", e);
        }
    }

    static boolean containsTargetCallSite(byte[] classBytes) {
        boolean[] found = {false};
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(new ClassVisitor(ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (found[0]) return null;
                return new MethodVisitor(ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName,
                                                String mDescriptor, boolean isInterface) {
                        if (opcode == INVOKESTATIC
                                && JDTUTILS_INTERNAL_NAME.equals(owner)
                                && TARGET_METHOD.equals(mName)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    byte[] patchCallerClass(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        boolean[] patched = {false};

        ClassVisitor visitor = new ClassVisitor(ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new CallSiteWrappingVisitor(mv, patched);
            }
        };

        reader.accept(visitor, 0);
        return patched[0] ? writer.toByteArray() : null;
    }

    static class CallSiteWrappingVisitor extends MethodVisitor {
        private final boolean[] patched;

        CallSiteWrappingVisitor(MethodVisitor mv, boolean[] patched) {
            super(ASM9, mv);
            this.patched = patched;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            if (opcode == INVOKESTATIC
                    && JDTUTILS_INTERNAL_NAME.equals(owner)
                    && TARGET_METHOD.equals(name)) {

                Label tryStart = new Label();
                Label tryEnd = new Label();
                Label catchHandler = new Label();
                Label afterCatch = new Label();

                mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, NPE_INTERNAL_NAME);
                mv.visitLabel(tryStart);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                mv.visitLabel(tryEnd);
                mv.visitJumpInsn(GOTO, afterCatch);
                mv.visitLabel(catchHandler);
                mv.visitInsn(POP);
                mv.visitMethodInsn(INVOKESTATIC, COLLECTIONS_INTERNAL_NAME,
                        "emptyList", "()Ljava/util/List;", false);
                mv.visitLabel(afterCatch);

                patched[0] = true;
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    private static class SafeClassWriter extends ClassWriter {
        SafeClassWriter(ClassReader classReader, int flags) {
            super(classReader, flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (Exception e) {
                return "java/lang/Object";
            }
        }
    }
}
