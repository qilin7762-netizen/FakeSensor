package com.app.fakesensor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static boolean zygoteHooked;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        XposedBridge.log("[FakeSensor] initZygote: hooking framework classes");

        SensorHook.init();
        SensorHook.hookSensorManagerInClassLoader(null); // null = boot classloader

        zygoteHooked = true;
        XposedBridge.log("[FakeSensor] Zygote hook installed, will be inherited by all app processes");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Zygote hook 已经覆盖了所有进程，handleLoadPackage 中只做补充
        if (!zygoteHooked) {
            SensorHook.init();
            SensorHook.hookSensorManagerInClassLoader(lpparam.classLoader);
        }
    }
}
