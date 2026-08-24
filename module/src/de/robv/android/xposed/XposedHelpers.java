package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Compile-time stub for LSPosed Xposed API.
 */
public class XposedHelpers {
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) { return null; }
    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) { return null; }
    public static void hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook callback) {}
    public static void hookAllConstructors(Class<?> clazz, XC_MethodHook callback) {}

    public static void setBooleanField(Object obj, String fieldName, boolean value) {}
    public static void setIntField(Object obj, String fieldName, int value) {}
    public static void setLongField(Object obj, String fieldName, long value) {}
    public static void setStringField(Object obj, String fieldName, String value) {}
    public static void setObjectField(Object obj, String fieldName, Object value) {}
    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static boolean getBooleanField(Object obj, String fieldName) { return false; }
    public static int getIntField(Object obj, String fieldName) { return 0; }
    public static long getLongField(Object obj, String fieldName) { return 0; }
    public static String getStringField(Object obj, String fieldName) { return null; }

    public static Class<?> findClass(String className, ClassLoader classLoader) { return null; }
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) { return null; }

    public static Field findField(Class<?> clazz, String fieldName) { return null; }
    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) { return null; }
}
