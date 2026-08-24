package com.eta.fanqie.enhance;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 番茄畅听增强模块 v19
 * 修复 v18.1 底部误伤：领现金/商城 一律只隐藏自身(底部tab)，绝不触底；
 * hideChain 增加底部导航栏保护(屏幕底部+全宽+扁平容器不藏)，兜底防误伤。
 * 其余机制不变：OnGlobalLayout 持续监听 + 非底部入口触底隐藏父框(3层/页面级保护)。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "FanqieEnhance";

    private static final String[] BLOCKED = {
            "com.xs.fm.live.impl.ecom.mall.NativeMallActivity",
            "com.bytedance.ug.sdk.luckycat.",
            "com.dragon.read.ad.dark.ui.",
            "com.dragon.read.ad.exciting.video.AdBrowserActivity",
            "com.dragon.read.ad.immersive.ImmersiveActivity",
            "com.dragon.read.admodule.adfm.landing.activity.",
            "com.dragon.read.pages.splash.ad.",
            "com.dragon.read.reader.speech.ad.",
            "com.dragon.read.pages.freeadvertising.",
    };

    private ClassLoader appCl;
    private int patchTries = 0;
    private final Set<String> seenGold = new HashSet<>();
    private int coinEntryId = -1;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.xs.fm".equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log("[" + TAG + "] 加载: process=" + lpparam.processName);
        this.appCl = lpparam.classLoader;
        hookActivityBlocker();
        hookDialogBlocker();
    }

    private void hookActivityBlocker() {
        final Handler h = new Handler(Looper.getMainLooper());
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try { patchVip(); } catch (Throwable ignored) {}
                }
            });

            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object act = param.thisObject;
                    if (act == null) return;
                    String name = act.getClass().getName();
                    for (String p : BLOCKED) {
                        if (name.startsWith(p)) {
                            try {
                                ((Activity) act).finish();
                                XposedBridge.log("[" + TAG + "] 已拦截页面: " + name);
                            } catch (Throwable ignored) {}
                            break;
                        }
                    }
                    patchTries = 0;
                    h.postDelayed(new Runnable() { public void run() { patchVip(); } }, 100);
                    h.postDelayed(new Runnable() { public void run() { patchVip(); } }, 600);
                    h.postDelayed(new Runnable() { public void run() { patchVip(); } }, 1500);
                    startHideWatch((Activity) act, h);
                }
            });
            XposedBridge.log("[" + TAG + "] Activity 拦截器+VIP patch 已启用");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] hookActivityBlocker 失败: " + t);
        }
    }

    /** 首轮120ms + 前30秒高频轮询(300ms)之后1.5s + OnGlobalLayout(限频120ms) */
    private void startHideWatch(final Activity act, final Handler h) {
        final WeakReference<Activity> wref = new WeakReference<>(act);
        h.postDelayed(new Runnable() {
            public void run() {
                Activity a = wref.get();
                if (a != null) { try { hideAll(a); } catch (Throwable ignored) {} }
            }
        }, 120);
        final long t0 = System.currentTimeMillis();
        h.postDelayed(new Runnable() {
            public void run() {
                Activity a = wref.get();
                if (a == null || a.isFinishing()) return;
                try { hideAll(a); } catch (Throwable ignored) {}
                h.postDelayed(this, (System.currentTimeMillis() - t0) < 30000 ? 300 : 1500);
            }
        }, 300);
        try {
            final View decor = act.getWindow().getDecorView();
            final WeakReference<Activity> ref = new WeakReference<>(act);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                private long last = 0;
                @Override
                public void onGlobalLayout() {
                    Activity a = ref.get();
                    if (a == null) return;
                    long now = System.currentTimeMillis();
                    if (now - last < 120) return;
                    last = now;
                    hideAll(a);
                }
            });
        } catch (Throwable ignored) {}
    }

    private int hideAll(Activity act) {
        int[] cnt = {0};
        try {
            View decor = act.getWindow().getDecorView();
            scanAll(decor, 0, cnt, act);
            scanRadioGroup(decor, cnt);
        } catch (Throwable ignored) {}
        try {
            scanAllWindows(act, cnt);
        } catch (Throwable ignored) {}
        return cnt[0];
    }

    /** 扫描进程内所有窗口根View（覆盖悬浮窗/额外 window 里的领金币入口） */
    private void scanAllWindows(Activity act, int[] cnt) throws Exception {
        Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
        Object inst = wmg.getMethod("getInstance").invoke(null);
        Field f = wmg.getDeclaredField("mViews");
        f.setAccessible(true);
        Object o = f.get(inst);
        if (!(o instanceof java.util.List)) return;
        java.util.List<?> list = (java.util.List<?>) o;
        for (int i = 0; i < list.size(); i++) {
            Object v;
            try { v = list.get(i); } catch (Throwable t) { break; }
            if (v instanceof View) {
                try {
                    scanAll((View) v, 0, cnt, act);
                    scanRadioGroup((View) v, cnt);
                } catch (Throwable ignored) {}
            }
        }
    }

    private void scanAll(View v, int depth, int[] cnt, Activity act) {
        if (v == null || depth > 40) return;
        if (coinEntryId == -1 && act != null) {
            try { coinEntryId = act.getResources().getIdentifier("h80", "id", act.getPackageName()); }
            catch (Throwable t) { coinEntryId = 0; }
        }
        if (coinEntryId > 0 && v.getId() == coinEntryId) {
            try {
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                View dec = act.getWindow().getDecorView();
                int sw = dec.getWidth(), sh = dec.getHeight();
                boolean topRight = loc[1] >= 0 && loc[1] < sh * 0.18f && (loc[0] + v.getWidth()) > sw * 0.5f;
                if (topRight && v.getVisibility() != View.GONE) {
                    v.setVisibility(View.GONE);
                    cnt[0]++;
                    XposedBridge.log("[" + TAG + "] 已隐藏金币入口(h80) loc=[" + loc[0] + "," + loc[1] + "] cls=" + v.getClass().getName());
                }
            } catch (Throwable ignored) {}
            return;
        }
        try {
            if (v instanceof TextView) {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null) {
                    String t = cs.toString().trim();
                    if (t.length() > 0 && t.length() <= 20) {
                        if (shouldHide(t)) {
                            boolean shallow = t.contains("金币")
                                    || (t.contains("广告") && (t.contains("免") || t.contains("看")));
                            if (shallow) hideShallow(v, t, cnt, act); else hideEntry(v, t, cnt, act);
                            return;
                        } else if (t.contains("广告") && !seenGold.contains(t)) {
                            seenGold.add(t);
                            XposedBridge.log("[" + TAG + "] 侦察金币文本: '" + t + "' cls=" + v.getClass().getName()
                                    + " parent=" + (v.getParent() != null ? v.getParent().getClass().getName() : "null"));
                        }
                    }
                }
            }
            if (v.getContentDescription() != null) {
                String cd = v.getContentDescription().toString().trim();
                if (cd.length() > 0 && cd.length() <= 20 && shouldHide(cd)) {
                    boolean shallowCd = cd.contains("金币")
                            || (cd.contains("广告") && (cd.contains("免") || cd.contains("看")));
                    if (shallowCd) hideShallow(v, cd, cnt, act); else hideEntry(v, cd, cnt, act);
                    return;
                }
            }
        } catch (Throwable ignored) {}
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                scanAll(vg.getChildAt(i), depth + 1, cnt, act);
            }
        }
    }

    private void scanRadioGroup(View v, int[] cnt) {
        if (v == null) return;
        if (v instanceof RadioGroup) {
            ViewGroup rg = (ViewGroup) v;
            for (int i = 0; i < rg.getChildCount(); i++) {
                View c = rg.getChildAt(i);
                String txt = findText(c);
                if (txt != null && (txt.contains("商城") || txt.contains("领现金"))) {
                    if (c.getVisibility() != View.GONE) {
                        c.setVisibility(View.GONE);
                        cnt[0]++;
                        XposedBridge.log("[" + TAG + "] 已隐藏底部tab['" + txt + "'] view=" + c.getClass().getName());
                    }
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                scanRadioGroup(vg.getChildAt(i), cnt);
            }
        }
    }

    private String findText(View v) {
        if (v == null) return null;
        if (v instanceof TextView) {
            CharSequence cs = ((TextView) v).getText();
            return cs == null ? null : cs.toString().trim();
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                String t = findText(vg.getChildAt(i));
                if (t != null && t.length() > 0) return t;
            }
        }
        return null;
    }

    private boolean shouldHide(String t) {
        if (t.contains("资产")) return true;
        if (t.contains("购物车") || t.contains("优惠券")) return true;
        if (t.contains("立即领取")) return true;
        if (t.contains("直播") && t.length() <= 4) return true;
        if (t.contains("现金") && !t.matches(".*[0-9].*")) return true;
        if (t.contains("福利") && t.length() <= 8) return true;
        if (t.contains("商城") || t.contains("领现金")) return true;
        if (t.contains("金币")) return true;
        if (t.contains("广告") && (t.contains("免") || t.contains("看"))) return true;
        return false;
    }

    /** 隐藏入口：底部tab(商城/领现金)无条件只藏自身；普通条目触底隐藏父框 */
    private void hideEntry(View v, String t, int[] cnt, Activity act) {
        if (t.contains("商城") || t.contains("领现金")) {
            hideTabSelf(v, t, cnt);
            return;
        }
        View p = (View) v.getParent();
        if (p == null) return;
        if (p instanceof RadioGroup) {
            hideTabSelf(v, t, cnt);
            return;
        }
        String cls = p.getClass().getName();
        if (cls.contains("TabView")) {
            hideTabSelf(p, t, cnt);
            return;
        }
        hideChain(p, t, cnt, act);
    }

/** 浅隐藏：自身+直接父容器；父过宽(可能顶栏)或导航tab时只藏自身 */
    private void hideShallow(View v, String t, int[] cnt, Activity act) {
        int screenW = 0;
        try { screenW = act.getWindow().getDecorView().getWidth(); } catch (Throwable ignored) {}
        View p = (View) v.getParent();
        if (p != null && !(p instanceof RadioGroup)
                && screenW > 0 && p.getWidth() < screenW * 0.6f
                && p.getVisibility() != View.GONE) {
            p.setVisibility(View.GONE);
            cnt[0]++;
            XposedBridge.log("[" + TAG + "] 已浅隐藏['" + t + "'] 父=" + p.getClass().getName());
            return;
        }
        hideTabSelf(v, t, cnt);
    }

    private void hideTabSelf(View tab, String t, int[] cnt) {
        if (tab.getVisibility() != View.GONE) {
            tab.setVisibility(View.GONE);
            cnt[0]++;
            XposedBridge.log("[" + TAG + "] 已隐藏tab['" + t + "'] " + tab.getClass().getName());
        }
    }

    /** 触底隐藏父链：向上连藏3层容器；页面级容器保护 + 底部导航栏保护 */
    private void hideChain(View start, String t, int[] cnt, Activity act) {
        View cur = start;
        int depth = 0;
        int hid = 0;
        int screenW = 0, screenH = 0;
        try {
            View decor = act.getWindow().getDecorView();
            screenW = decor.getWidth();
            screenH = decor.getHeight();
        } catch (Throwable ignored) {}
        while (cur != null && depth < 3) {
            if (screenW > 0 && screenH > 0) {
                int w = cur.getWidth();
                int h = cur.getHeight();
                int top = cur.getTop();
                // 页面级容器保护
                if (w >= screenW * 0.9f && h >= screenH * 0.7f) break;
                // 底部导航栏保护：屏幕底部 + 全宽 + 扁平容器，不藏
                if (top > screenH * 0.78f && h < screenH * 0.2f && w >= screenW * 0.8f) break;
            }
            if (isNavBar(cur)) break; // 文本级底部导航栏保护
            if (cur.getVisibility() != View.GONE) {
                cur.setVisibility(View.GONE);
                hid++;
            }
            cur = (View) cur.getParent();
            depth++;
        }
        if (hid > 0) {
            cnt[0] += hid;
            XposedBridge.log("[" + TAG + "] 已触底隐藏['" + t + "'] " + depth + "层 根=" + start.getClass().getName());
        }
    }

    private void patchVip() {
        if (appCl == null) return;
        try {
            Class<?> acct = Class.forName("com.dragon.read.user.AcctManager", true, appCl);
            Field instF = acct.getDeclaredField("INSTANCE");
            instF.setAccessible(true);
            Object acctObj = instF.get(null);
            if (acctObj == null) { retry(); return; }
            Field umF = acct.getDeclaredField("userModel");
            umF.setAccessible(true);
            Object model = umF.get(acctObj);
            if (model == null) { retry(); return; }
            setField(model, "isVip", true);
            setField(model, "freeAd", true);
            setField(model, "expireTime", "2099-12-31");
            setField(model, "leftTime", "999999999");
            trySetField(model, "reverseVIP", true);
            trySetField(model, "freeAdLeft", 999999999L);
            trySetField(model, "freeAdExpire", 4102444800000L);
            XposedBridge.log("[" + TAG + "] 已patch userModel isVip=true");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] patchVip 异常: " + t);
            retry();
        }
    }

    private void retry() {
        if (patchTries++ < 10) {
            final Handler h = new Handler(Looper.getMainLooper());
            h.postDelayed(new Runnable() { public void run() { patchVip(); } }, 400);
        }
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private void trySetField(Object obj, String name, Object value) {
        try { setField(obj, name, value); } catch (Throwable ignored) {}
    }

    private void hookDialogBlocker() {
        final Handler h = new Handler(Looper.getMainLooper());
        try {
            XposedHelpers.findAndHookMethod(Dialog.class, "show", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        final Dialog dlg = (Dialog) param.thisObject;
                        if (dlg == null) return;
                        String name = dlg.getClass().getName();
                        boolean classHit = name.contains("luckycat") || name.contains("Lucky")
                                || name.contains("Update") || name.contains("Upgrade")
                                || name.contains("AdDialog") || name.contains("AdPop")
                                || name.contains("Advert");
                        if (classHit) {
                            dlg.dismiss();
                            XposedBridge.log("[" + TAG + "] 已拦截弹窗(类名): " + name);
                            return;
                        }
                        h.postDelayed(new Runnable() {
                            @Override public void run() {
                                try {
                                    if (!dlg.isShowing()) return;
                                    String hit = findBadDialogText(dlg);
                                    if (hit != null) {
                                        dlg.dismiss();
                                        XposedBridge.log("[" + TAG + "] 已拦截弹窗(内容:'" + hit + "'): " + name);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }, 400);
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[" + TAG + "] Dialog 拦截器已启用");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Dialog 拦截器失败: " + t);
        }
    }

    private String findBadDialogText(Dialog dlg) {
        try {
            View decor = dlg.getWindow().getDecorView();
            return findBadText(decor, 0);
        } catch (Throwable t) {
            return null;
        }
    }

    private String findBadText(View v, int depth) {
        if (v == null || depth > 12) return null;
        if (v instanceof TextView) {
            try {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null) {
                    String t = cs.toString().trim();
                    if (t.length() > 0 && t.length() <= 16 && isDialogBad(t)) {
                        return t;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                String r = findBadText(vg.getChildAt(i), depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private boolean isDialogBad(String t) {
        if (t.contains("签到") && t.length() <= 8) return true;
        if (t.contains("听歌")) return true;
        if (t.contains("领取") && t.contains("金币")) return true;
        return false;
    }


    private static final String[] NAV_TABS = {"首页", "听歌", "我的"};

    /** 文本级导航栏检测：容器同时含 >=2 个底部 tab 精确文本即视为导航栏/页面级容器 */
    private boolean isNavBar(View v) {
        int hit = 0;
        for (String s : NAV_TABS) {
            if (containsExactText(v, s)) hit++;
            if (hit >= 2) return true;
        }
        return false;
    }

    private boolean containsExactText(View v, String s) {
        if (v == null) return false;
        if (v instanceof TextView) {
            try {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null && s.equals(cs.toString().trim())) return true;
            } catch (Throwable ignored) {}
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (containsExactText(vg.getChildAt(i), s)) return true;
            }
        }
        return false;
    }
}
