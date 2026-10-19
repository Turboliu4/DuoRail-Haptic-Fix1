package de.robv.android.xposed;
public final class XposedBridge {
    private XposedBridge() {}
    public static void log(String message) { }
    public static java.util.Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> clazz, String method, XC_MethodHook cb) { return null; }
    public static XC_MethodHook.Unhook hookMethod(java.lang.reflect.Member method, XC_MethodHook cb) { return null; }
}
