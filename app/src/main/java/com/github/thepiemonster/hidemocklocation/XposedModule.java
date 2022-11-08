package com.github.thepiemonster.hidemocklocation;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class XposedModule implements IXposedHookZygoteInit, IXposedHookLoadPackage {

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
                XposedHelpers.findAndHookMethod(clazz, "onReportLocation", boolean.class, Location.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                    }
                });
            }
        } else {
            handleLoadPackageForApps(lpparam);
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private void handleLoadPackageForApps(XC_LoadPackage.LoadPackageParam lpparam) {
        // Additional info - not implemented - probably will not be implemented in future:
        //
        // There is one more method - getUriFor. Its returned value can be used
        // to listen for setting changes, without getting any settings values.
        // (Low risk of checking something only with this way)

        // Google Play Services
        XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader, "getExtras", hideMockGooglePlayServicesHook.init(lpparam.processName, lpparam.packageName));

        // New way of checking if location is mocked, SDK 18+
        // deprecated in API level 31
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader, "isFromMockProvider", hideMockProviderHook.init(lpparam.processName, lpparam.packageName));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader, "isMock", hideMockProviderHook.init(lpparam.processName, lpparam.packageName));

        // Self hook - informing Activity that Xposed module is enabled
        if (lpparam.packageName.equals(Common.PACKAGE_NAME))
            XposedHelpers.findAndHookMethod(Common.ACTIVITY_NAME, lpparam.classLoader, "isModuleEnabled", XC_MethodReplacement.returnConstant(true));
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        hideMockProviderHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(false);
            }
        };

        hideMockGooglePlayServicesHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Bundle extras = (Bundle) param.getResult();
                if (extras != null && extras.getBoolean(Common.GMS_MOCK_KEY))
                    extras.putBoolean(Common.GMS_MOCK_KEY, false);
                param.setResult(extras);

            }
        };
    }
}
