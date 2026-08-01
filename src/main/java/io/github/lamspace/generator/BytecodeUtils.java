package io.github.lamspace.generator;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Shared bytecode emission helpers used by both class and interface
 * proxy generators. All methods are static and stateless.
 */
final class BytecodeUtils {

    private BytecodeUtils() {}

    static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    static int loadOpcode(Class<?> type) {
        if (type == double.class) return Opcodes.DLOAD;
        if (type == float.class) return Opcodes.FLOAD;
        if (type == long.class) return Opcodes.LLOAD;
        if (type == int.class || type == boolean.class || type == byte.class
                || type == char.class || type == short.class)
            return Opcodes.ILOAD;
        return Opcodes.ALOAD;
    }

    static void boxPrimitive(MethodVisitor mv, Class<?> type) {
        String wrapper;
        String desc;
        if (type == boolean.class) {
            wrapper = "java/lang/Boolean";
            desc = "(Z)Ljava/lang/Boolean;";
        } else if (type == byte.class) {
            wrapper = "java/lang/Byte";
            desc = "(B)Ljava/lang/Byte;";
        } else if (type == char.class) {
            wrapper = "java/lang/Character";
            desc = "(C)Ljava/lang/Character;";
        } else if (type == short.class) {
            wrapper = "java/lang/Short";
            desc = "(S)Ljava/lang/Short;";
        } else if (type == int.class) {
            wrapper = "java/lang/Integer";
            desc = "(I)Ljava/lang/Integer;";
        } else if (type == float.class) {
            wrapper = "java/lang/Float";
            desc = "(F)Ljava/lang/Float;";
        } else if (type == long.class) {
            wrapper = "java/lang/Long";
            desc = "(J)Ljava/lang/Long;";
        } else if (type == double.class) {
            wrapper = "java/lang/Double";
            desc = "(D)Ljava/lang/Double;";
        } else {
            return;
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper, "valueOf", desc, false);
    }

    static void unboxPrimitive(MethodVisitor mv, Class<?> type) {
        String wrapper;
        String unboxMethod;
        String desc;
        if (type == boolean.class) {
            wrapper = "java/lang/Boolean";
            unboxMethod = "booleanValue";
            desc = "()Z";
        } else if (type == byte.class) {
            wrapper = "java/lang/Byte";
            unboxMethod = "byteValue";
            desc = "()B";
        } else if (type == char.class) {
            wrapper = "java/lang/Character";
            unboxMethod = "charValue";
            desc = "()C";
        } else if (type == short.class) {
            wrapper = "java/lang/Short";
            unboxMethod = "shortValue";
            desc = "()S";
        } else if (type == int.class) {
            wrapper = "java/lang/Integer";
            unboxMethod = "intValue";
            desc = "()I";
        } else if (type == float.class) {
            wrapper = "java/lang/Float";
            unboxMethod = "floatValue";
            desc = "()F";
        } else if (type == long.class) {
            wrapper = "java/lang/Long";
            unboxMethod = "longValue";
            desc = "()J";
        } else if (type == double.class) {
            wrapper = "java/lang/Double";
            unboxMethod = "doubleValue";
            desc = "()D";
        } else {
            return;
        }
        mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, unboxMethod, desc, false);
    }

    /**
     * Pushes a Class constant onto the stack.
     * For primitives, uses wrapper.TYPE (e.g., Integer.TYPE).
     * For reference types, uses LDC.
     */
    static void pushClassConstant(MethodVisitor mv, Class<?> type) {
        if (type.isPrimitive()) {
            String wrapper = getWrapperInternalName(type);
            mv.visitFieldInsn(Opcodes.GETSTATIC, wrapper, "TYPE", "Ljava/lang/Class;");
        } else {
            mv.visitLdcInsn(Type.getType(type));
        }
    }

    private static String getWrapperInternalName(Class<?> primitiveType) {
        if (primitiveType == int.class) return "java/lang/Integer";
        if (primitiveType == long.class) return "java/lang/Long";
        if (primitiveType == double.class) return "java/lang/Double";
        if (primitiveType == float.class) return "java/lang/Float";
        if (primitiveType == boolean.class) return "java/lang/Boolean";
        if (primitiveType == byte.class) return "java/lang/Byte";
        if (primitiveType == char.class) return "java/lang/Character";
        if (primitiveType == short.class) return "java/lang/Short";
        if (primitiveType == void.class) return "java/lang/Void";
        throw new IllegalArgumentException("Unknown primitive: " + primitiveType);
    }
}
