package com.bazel.jdt;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class JDTUtilsPatcherTest implements Opcodes {

    private static final String SIMPLE_DESC = "()Ljava/util/List;";

    @Test
    public void patchCallerClass_wrapsCallSite() {
        byte[] caller = buildCallerClass();
        JDTUtilsPatcher patcher = new JDTUtilsPatcher();
        byte[] patched = patcher.patchCallerClass(caller);

        assertNotNull("should patch class that calls searchDecompiledSources", patched);
        assertFalse("patched bytes should differ",
                java.util.Arrays.equals(caller, patched));
    }

    @Test
    public void patchCallerClass_skipsClassWithNoCallSite() {
        byte[] unrelated = buildUnrelatedClass();
        JDTUtilsPatcher patcher = new JDTUtilsPatcher();
        byte[] patched = patcher.patchCallerClass(unrelated);

        assertNull("should return null for class without searchDecompiledSources call", patched);
    }

    @Test
    public void patchCallerClass_skipsWhenOwnerDiffers() {
        byte[] wrongOwner = buildCallerWithDifferentOwner();
        JDTUtilsPatcher patcher = new JDTUtilsPatcher();
        byte[] patched = patcher.patchCallerClass(wrongOwner);

        assertNull("should not patch calls to other classes", patched);
    }

    @Test
    public void patchedCallSite_returnsEmptyListInsteadOfNPE() throws Exception {
        byte[] fakeJDTUtils = buildFakeJDTUtils();
        byte[] caller = buildCallerClass();

        JDTUtilsPatcher patcher = new JDTUtilsPatcher();
        byte[] patchedCaller = patcher.patchCallerClass(caller);
        assertNotNull(patchedCaller);

        TestClassLoader loader = new TestClassLoader();
        loader.define("org.eclipse.jdt.ls.core.internal.JDTUtils", fakeJDTUtils);
        Class<?> callerClass = loader.define("TestCaller", patchedCaller);

        Method method = callerClass.getMethod("callSearch");
        Object result = method.invoke(null);

        assertNotNull("patched call site should return non-null", result);
        assertTrue("should return a List", result instanceof List);
        assertTrue("should return empty list", ((List<?>) result).isEmpty());
    }

    private byte[] buildCallerClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC, "TestCaller", null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "callSearch", SIMPLE_DESC, null, null);
        mv.visitCode();
        mv.visitMethodInsn(INVOKESTATIC, JDTUtilsPatcher.JDTUTILS_INTERNAL_NAME,
                JDTUtilsPatcher.TARGET_METHOD, SIMPLE_DESC, false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] buildFakeJDTUtils() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC,
                JDTUtilsPatcher.JDTUTILS_INTERNAL_NAME, null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                JDTUtilsPatcher.TARGET_METHOD, SIMPLE_DESC, null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, "java/lang/NullPointerException");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("occurrences is null");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/NullPointerException",
                "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(3, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] buildUnrelatedClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC, "UnrelatedClass", null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "doSomething", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] buildCallerWithDifferentOwner() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC, "WrongOwnerCaller", null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "callSearch", SIMPLE_DESC, null, null);
        mv.visitCode();
        mv.visitMethodInsn(INVOKESTATIC, "com/other/Utils",
                JDTUtilsPatcher.TARGET_METHOD, SIMPLE_DESC, false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 0);
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
