package com.github.thepiemonster.hidemocklocation;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class XposedModule implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public XC_ProcessNameMethodHook hideAllowMockSettingHook;
    public XC_ProcessNameMethodHook hideMockProviderHook;
    public XC_ProcessNameMethodHook hideMockGooglePlayServicesHook;

    // Hook with additional member - processName
    // Used to whitelisting/blacklisting apps
    static class XC_ProcessNameMethodHook extends XC_MethodHook {

        String processName;
        String packageName;

        private XC_MethodHook init(String processName, String packageName) {
            this.processName = processName;
            this.packageName = packageName;
            return this;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null) {
            return;
        }

        if (lpparam.packageName.equals("android")) {
            /*
             * Disable GNSSLocation
             *
             * check Android_"Refactor Java GNSS HAL" commit.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @SuppressLint("PrivateApi")
                Class<?> clazz = lpparam.classLoader.loadClass("com.android.server.location.gnss.GnssLocationProvider");
                XposedHelpers.findAndHookMethod(clazz, "onReportLocation",
                        boolean.class, Location.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(null);
                            }
                        }
                );
            }
        } else {
            handleLoadPackageForApps(lpparam);
        }
    }

    private void handleLoadPackageForApps(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hooking Settings.Secure API methods instead of internal methods - longer code, but more SDK independent.
        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getString",
                ContentResolver.class, String.class,
                hideAllowMockSettingHook.init(lpparam.processName, lpparam.packageName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getInt",
                ContentResolver.class, String.class,
                hideAllowMockSettingHook.init(lpparam.processName, lpparam.packageName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getInt",
                ContentResolver.class, String.class, int.class,
                hideAllowMockSettingHook.init(lpparam.processName, lpparam.packageName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getFloat",
                ContentResolver.class, String.class,
                hideAllowMockSettingHook.init(lpparam.processName, lpparam.packageName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getFloat",
                ContentResolver.class, String.class, float.class,
                hideAllowMockSettingHook.init(lpparam.processName, lpparam.packageName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getLong",
                ContentResolver.class, String.class,
                hideAllowMockSettingHook.init(lpparam.processName, lpparam.packageName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getLong",
                ContentResolver.class, String.class, long.class,
                hideAllowMockSettingHook.init(lpparam.processName, lpparam.packageName));

        // Additional info - not implemented - probably will not be implemented in future:
        //
        // There is one more method - getUriFor. Its returned value can be used
        // to listen for setting changes, without getting any settings values.
        // (Low risk of checking something only with this way)

        // Google Play Services
        XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader, "getExtras",
                hideMockGooglePlayServicesHook.init(lpparam.processName, lpparam.packageName));

        // New way of checking if location is mocked, SDK 18+
        if (Common.JB_MR2_NEWER)
            XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader,
                    "isFromMockProvider", hideMockProviderHook.init(lpparam.processName, lpparam.packageName));

        // Self hook - informing Activity that Xposed module is enabled
        if (lpparam.packageName.equals(Common.PACKAGE_NAME))
            XposedHelpers.findAndHookMethod(Common.ACTIVITY_NAME, lpparam.classLoader, "isModuleEnabled",
                    XC_MethodReplacement.returnConstant(true));
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        hideAllowMockSettingHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!Common.SYSTEM_WHITELIST.contains(this.processName) &&
                        !Common.SYSTEM_WHITELIST.contains(this.packageName)) {
                    String methodName = param.method.getName();
                    String setting = (String) param.args[1];
                    if (setting.equals(Settings.Secure.ALLOW_MOCK_LOCATION)) {
                        switch (methodName) {
                            case "getInt":
                                param.setResult(0);
                                break;
                            case "getString":
                                param.setResult("0");
                                break;
                            case "getFloat":
                                param.setResult(0.0f);
                                break;
                            case "getLong":
                                param.setResult(0L);
                                break;
                            default:
                                break;

                        }
                    }
                }
            }
        };

        hideMockProviderHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(false);
            }
        };

        hideMockGooglePlayServicesHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Bundle extras = (Bundle) param.getResult();
                if (extras != null && extras.getBoolean(Common.GMS_MOCK_KEY))
                    extras.putBoolean(Common.GMS_MOCK_KEY, false);
                param.setResult(extras);

            }
        };
    }
}