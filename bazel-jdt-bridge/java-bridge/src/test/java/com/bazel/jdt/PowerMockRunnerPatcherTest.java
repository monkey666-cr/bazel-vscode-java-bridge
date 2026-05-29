package com.bazel.jdt;

import static org.junit.Assert.*;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class PowerMockRunnerPatcherTest implements Opcodes {

    private static final String ITYPEBINDING_INTERNAL = "org/eclipse/jdt/core/dom/ITypeBinding";
    private static final String ITYPEBINDING_DESC = "(L" + ITYPEBINDING_INTERNAL + ";)Z";
    private static final String SEARCHER_INTERNAL = "com/microsoft/java/test/searcher/JUnit4TestSearcher";

    @Test
    public void containsIsTestClassCall_detectsCall() {
        byte[] classBytes = buildClassWithIsTestClassCall();
        assertTrue(PowerMockRunnerPatcher.containsIsTestClassCall(classBytes));
    }

    @Test
    public void containsIsTestClassCall_skipsMismatchedMethodName() {
        byte[] classBytes = buildClassWithDifferentMethodCall();
        assertFalse(PowerMockRunnerPatcher.containsIsTestClassCall(classBytes));
    }

    @Test
    public void containsIsTestClassCall_skipsWrongDescriptor() {
        byte[] classBytes = buildClassWithWrongDescriptor();
        assertFalse(PowerMockRunnerPatcher.containsIsTestClassCall(classBytes));
    }

    @Test
    public void patchTestSearchClass_patchesClassWithIsTestClassCall() {
        byte[] original = buildClassWithIsTestClassCall();
        PowerMockRunnerPatcher patcher = new PowerMockRunnerPatcher();
        byte[] patched = patcher.patchTestSearchClass(original);

        assertNotNull("should patch class with isTestClass call", patched);
        assertFalse("patched bytes should differ",
                java.util.Arrays.equals(original, patched));
    }

    @Test
    public void patchTestSearchClass_skipsClassWithoutTarget() {
        byte[] classBytes = buildClassWithDifferentMethodCall();
        PowerMockRunnerPatcher patcher = new PowerMockRunnerPatcher();
        byte[] patched = patcher.patchTestSearchClass(classBytes);

        assertNull("should return null for class without isTestClass call", patched);
    }

    @Test
    public void patchTestSearchClass_producesLoadableClass() {
        byte[] original = buildClassWithIsTestClassCall();
        PowerMockRunnerPatcher patcher = new PowerMockRunnerPatcher();
        byte[] patched = patcher.patchTestSearchClass(original);
        assertNotNull(patched);

        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.define("TestSearchUtils", patched);
        assertNotNull("patched class should be loadable", clazz);
    }

    private byte[] buildClassWithIsTestClassCall() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC, "TestSearchUtils", null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "findTests", "(L" + ITYPEBINDING_INTERNAL + ";)Z", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, SEARCHER_INTERNAL,
                PowerMockRunnerPatcher.IS_TEST_CLASS, ITYPEBINDING_DESC, false);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] buildClassWithDifferentMethodCall() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC, "OtherClass", null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "doSomething", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] buildClassWithWrongDescriptor() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC, "WrongDesc", null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "check", "(Ljava/lang/String;)Z", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, SEARCHER_INTERNAL,
                PowerMockRunnerPatcher.IS_TEST_CLASS, "(Ljava/lang/String;)Z", false);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static class TestClassLoader extends ClassLoader {
        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
