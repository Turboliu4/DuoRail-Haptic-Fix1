package io.github.duorail.haptic;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * DuoRail Haptic Fix 0.2.0 (experimental; made for MiDuo 0.78).
 *
 * SystemUI: captures EXACT MiDuo onClick/onTouch listener classes when installed,
 *           then hooks those listener methods. No global performClick or Log.w hook.
 * Launcher: desktop official Dock uses the native launcher click path, according
 *           to "DuoOfficialDock OUTER_LAUNCH ... container=-102" device logs.
 *           Launcher.onClick + View.performClick fallback are guarded by an exact
 *           ItemInfo.container == -102 test. No non-Dock haptics requested.
 *
 * Safe failure: if classes/fields/methods aren't present, the patch quietly does
 * nothing. It does NOT replace MiDuo handlers or change the click result.
 */
public final class DuoRailHook implements IXposedHookLoadPackage {
    private static final String SYSUI = "com.android.systemui";
    private static final String HOME = "com.miui.home";
    private static final String TAG = "DuoRailHapticFix";
    private static final String PREFIX = "dev.duo.workspaceprobe.Entry$$ExternalSyntheticLambda";
    private static final String NAV_TOUCH =
            "dev.duo.workspaceprobe.Entry$NavView$$ExternalSyntheticLambda1";
    private static final long MIN_GAP_MS = 120;
    private static final AtomicLong LAST_PULSE_MS = new AtomicLong(-10000);
    private static final Set<String> HOOKED_LISTENERS =
            Collections.synchronizedSet(new HashSet<String>());
    private static volatile String processPackage;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!SYSUI.equals(lp.packageName) && !HOME.equals(lp.packageName)) return;
        processPackage = lp.packageName;
        try {
            if (SYSUI.equals(lp.packageName)) installSystemUiHooks();
            else installHomeHooks(lp.classLoader);
            info("Installed 0.2.0 in " + lp.packageName);
        } catch (Throwable t) {
            error("handleLoadPackage", t);
        }
    }

    private static void installSystemUiHooks() {
        // View.setOnClickListener is observed at registration time, not per click.
        // Only a handful of exact MiDuo listener classes are hooked afterwards.
        try {
            XposedHelpers.findAndHookMethod(View.class, "setOnClickListener",
                    View.OnClickListener.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!(p.args[0] instanceof View.OnClickListener)) return;
                        View.OnClickListener listener = (View.OnClickListener) p.args[0];
                        String name = listener.getClass().getName();
                        if (isAllowedRailClickListener(name)) {
                            hookRailClickListener(listener.getClass());
                        }
                    } catch (Throwable t) { error("setOnClickListener", t); }
                }
            });
        } catch (Throwable t) { error("install setOnClickListener", t); }

        try {
            XposedHelpers.findAndHookMethod(View.class, "setOnTouchListener",
                    View.OnTouchListener.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!(p.args[0] instanceof View.OnTouchListener)) return;
                        View.OnTouchListener listener = (View.OnTouchListener) p.args[0];
                        if (NAV_TOUCH.equals(listener.getClass().getName())) {
                            hookNavTouchListener(listener.getClass());
                        }
                    } catch (Throwable t) { error("setOnTouchListener", t); }
                }
            });
        } catch (Throwable t) { error("install setOnTouchListener", t); }
    }

    // Confirmed by enumerating method definitions in MiDuo 0.78 classes.dex.
    private static boolean isAllowedRailClickListener(String name) {
        return (PREFIX + "16").equals(name)   // buildModePanels button
            || (PREFIX + "17").equals(name)   // buildModePanels button
            || (PREFIX + "76").equals(name)   // recent-task icon
            || (PREFIX + "107").equals(name)  // buildRail button
            || (PREFIX + "108").equals(name); // buildRail button
    }

    private static void hookRailClickListener(Class<?> listenerClass) {
        if (!HOOKED_LISTENERS.add(listenerClass.getName())) return;
        try {
            XposedBridge.hookAllMethods(listenerClass, "onClick", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (p.getThrowable() != null) return;
                        if (p.args.length > 0 && p.args[0] instanceof View) {
                            pulse((View) p.args[0], "MiDuo-click");
                        }
                    } catch (Throwable t) { error("rail onClick", t); }
                }
            });
            info("Hooked MiDuo listener: " + listenerClass.getName());
        } catch (Throwable t) { error("rail listener " + listenerClass.getName(), t); }
    }

    private static void hookNavTouchListener(Class<?> listenerClass) {
        if (!HOOKED_LISTENERS.add(listenerClass.getName())) return;
        try {
            XposedBridge.hookAllMethods(listenerClass, "onTouch", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (p.getThrowable() != null) return;
                        if (p.args.length < 2 || !(p.args[0] instanceof View)
                                || !(p.args[1] instanceof MotionEvent)) return;
                        MotionEvent ev = (MotionEvent) p.args[1];
                        if (ev.getActionMasked() == MotionEvent.ACTION_UP
                                && Boolean.TRUE.equals(p.getResult())) {
                            pulse((View) p.args[0], "MiDuo-NavView");
                        }
                    } catch (Throwable t) { error("nav onTouch", t); }
                }
            });
            info("Hooked MiDuo navigation touch handler");
        } catch (Throwable t) { error("nav listener", t); }
    }

    private static void installHomeHooks(ClassLoader loader) {
        // Narrow click-handler hook, if this launcher build has onClick(View).
        try {
            Class<?> launcher = Class.forName("com.miui.home.launcher.Launcher", false, loader);
            for (Method m : launcher.getDeclaredMethods()) {
                if ("onClick".equals(m.getName())
                        && m.getParameterTypes().length == 1
                        && View.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                if (p.getThrowable() != null) return;
                                if (p.args.length > 0 && p.args[0] instanceof View) {
                                    pulseOfficialDock((View) p.args[0], "Launcher.onClick");
                                }
                            } catch (Throwable t) { error("Launcher.onClick callback", t); }
                        }
                    });
                    info("Hooked Launcher.onClick for official dock");
                }
            }
        } catch (ClassNotFoundException e) {
            info("Launcher class absent; using checked-click fallback");
        } catch (Throwable t) { error("install Launcher.onClick", t); }

        // Compatibility fallback for app icons dispatched through View.performClick
        // rather than Launcher.onClick. Guarded on ItemInfo.container=-102 *before*
        // any vibrator call. Scope is Launcher ONLY, never SystemUI.
        try {
            XposedHelpers.findAndHookMethod(View.class, "performClick", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!Boolean.TRUE.equals(p.getResult())) return;
                        if (p.thisObject instanceof View) {
                            pulseOfficialDock((View) p.thisObject, "Dock.performClick");
                        }
                    } catch (Throwable t) { error("launcher checked performClick", t); }
                }
            });
            info("Installed launcher-only container-gated click fallback");
        } catch (Throwable t) { error("install launcher fallback", t); }
    }

    private static void pulseOfficialDock(View view, String source) {
        if (!HOME.equals(processPackage) || view == null) return;
        if (!isOfficialDockIcon(view)) return;
        pulse(view, source);
    }

    // From user's Logcat: DuoOfficialDock: OUTER_LAUNCH ... container=-102.
    // Don't vibrate ordinary desktop icons (container=-100), folders, recents,
    // background touches, etc. If this tag isn't discoverable, fail CLOSED.
    private static boolean isOfficialDockIcon(View view) {
        View current = view;
        for (int depth = 0; depth < 3 && current != null; depth++) {
            Object tag;
            try { tag = current.getTag(); }
            catch (Throwable ignored) { return false; }
            if (tag != null && tag.getClass().getName().startsWith("com.miui.home.")) {
                Long container = readContainerField(tag);
                if (container != null) return container == -102L;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static Long readContainerField(Object tag) {
        for (Class<?> type = tag.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("container");
                field.setAccessible(true);
                Object value = field.get(tag);
                return value instanceof Number ? ((Number) value).longValue() : null;
            } catch (NoSuchFieldException ignored) { /* check superclass */ }
              catch (Throwable ignored) { return null; }
        }
        return null;
    }

    private static void pulse(View view, String source) {
        if (view == null || !view.isEnabled()) return;
        final Context ctx;
        try { ctx = view.getContext(); }
        catch (Throwable ignored) { return; }
        if (ctx == null) return;
        long now = SystemClock.uptimeMillis();
        while (true) {
            long last = LAST_PULSE_MS.get();
            if (now - last < MIN_GAP_MS) return;
            if (LAST_PULSE_MS.compareAndSet(last, now)) break;
        }
        boolean triggered = false;
        try {
            triggered = view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Throwable ignored) { /* try service fallback */ }
        if (!triggered) {
            try {
                Vibrator vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build();
                    vibrator.vibrate(VibrationEffect.createPredefined(
                            VibrationEffect.EFFECT_CLICK), attrs);
                    triggered = true;
                }
            } catch (Throwable t) { error("vibrator fallback", t); }
        }
        if (triggered) Log.i(TAG, "Haptic requested: " + source);
    }

    private static void info(String message) {
        try { Log.i(TAG, message); } catch (Throwable ignored) { }
    }
    private static void error(String message, Throwable t) {
        try { XposedBridge.log(TAG + " " + message + ": " + t); }
        catch (Throwable ignored) { }
    }
}
