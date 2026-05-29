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

public class PowerMockRunnerPatcher implements WeavingHook, Opcodes {

    private static final Logger LOG = Logger.getLogger(PowerMockRunnerPatcher.class.getName());

    private static final String TARGET_BUNDLE = "com.microsoft.java.test.plugin";
    static final String IS_TEST_CLASS = "isTestClass";
    private static final String ITYPEBINDING_DESC_FRAGMENT = "ITypeBinding";
    private static final String HELPER_INTERNAL = "com/bazel/jdt/PowerMockHelper";
    private static final String ITYPEBINDING_INTERNAL = "org/eclipse/jdt/core/dom/ITypeBinding";

    @Override
    public void weave(WovenClass wovenClass) {
        String bundleName = wovenClass.getBundleWiring().getBundle().getSymbolicName();
        if (!TARGET_BUNDLE.equals(bundleName)) {
            return;
        }

        byte[] original = wovenClass.getBytes();
        if (!containsIsTestClassCall(original)) {
            return;
        }

        try {
            byte[] patched = patchTestSearchClass(original);
            if (patched != null) {
                wovenClass.getDynamicImports().add("com.bazel.jdt");
                wovenClass.setBytes(patched);
                LOG.info("Patched " + wovenClass.getClassName()
                        + ": injected PowerMock guard at isTestClass call site");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to patch " + wovenClass.getClassName() + ", leaving class unmodified", e);
        }
    }

    static boolean containsIsTestClassCall(byte[] classBytes) {
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
                                                String mDesc, boolean isInterface) {
                        if (IS_TEST_CLASS.equals(mName)
                                && mDesc.contains(ITYPEBINDING_DESC_FRAGMENT)
                                && mDesc.endsWith(")Z")) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    byte[] patchTestSearchClass(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new SafeClassWriter(reader,
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        boolean[] patched = {false};

        ClassVisitor visitor = new ClassVisitor(ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new PowerMockGuardInjectingVisitor(mv, patched);
            }
        };

        reader.accept(visitor, 0);
        return patched[0] ? writer.toByteArray() : null;
    }

    static class PowerMockGuardInjectingVisitor extends MethodVisitor {
        private final boolean[] patched;
        private int lastAloadVar = -1;

        PowerMockGuardInjectingVisitor(MethodVisitor mv, boolean[] patched) {
            super(ASM9, mv);
            this.patched = patched;
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            if (opcode == ALOAD) {
                lastAloadVar = varIndex;
            }
            super.visitVarInsn(opcode, varIndex);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

            if (IS_TEST_CLASS.equals(name)
                    && descriptor.contains(ITYPEBINDING_DESC_FRAGMENT)
                    && descriptor.endsWith(")Z")
                    && lastAloadVar >= 0) {
                injectPowerMockGuard(lastAloadVar);
                patched[0] = true;
            }
            lastAloadVar = -1;
        }

        private void injectPowerMockGuard(int typeBindingVar) {
            Label done = new Label();

            mv.visitInsn(DUP);
            mv.visitJumpInsn(IFEQ, done);

            mv.visitVarInsn(ALOAD, typeBindingVar);
            mv.visitMethodInsn(INVOKEINTERFACE, ITYPEBINDING_INTERNAL,
                    "getQualifiedName", "()Ljava/lang/String;", true);
            mv.visitMethodInsn(INVOKESTATIC, HELPER_INTERNAL,
                    "isPowerMockRunner", "(Ljava/lang/String;)Z", false);
            mv.visitJumpInsn(IFEQ, done);

            mv.visitInsn(POP);
            mv.visitInsn(ICONST_0);

            mv.visitLabel(done);
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
