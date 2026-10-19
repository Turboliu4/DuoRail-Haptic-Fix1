package de.robv.android.xposed;
// Compile-only API signature stub. Real class is supplied by LSPosed at runtime.
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }
    public static class Unhook { }
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object getResult() { return null; }
        public Throwable getThrowable() { return null; }
    }
}
