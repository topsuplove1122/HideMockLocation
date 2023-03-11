package com.github.thepiemonster.hidemocklocation;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class XposedModule implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public XC_MethodHook hideMockProviderHook;
    public XC_MethodHook hideMockGooglePlayServicesHook;

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

                Method handleReportLocation = XposedHelpers.findMethodExactIfExists(clazz, "handleReportLocation", boolean.class, Location.class);
                if (handleReportLocation != null) {
                    XposedBridge.hookMethod(handleReportLocation, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });
                }
            }
        }
        // Self hook - informing Activity that Xposed module is enabled
        else if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            XposedHelpers.findAndHookMethod(MainActivity.class,
                    "isModuleEnabled",
                    XC_MethodReplacement.returnConstant(true));
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
        XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader, "getExtras", hideMockGooglePlayServicesHook);

        // New way of checking if location is mocked, SDK 18+
        // deprecated in API level 31
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader, "isFromMockProvider", hideMockProviderHook);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader, "isMock", hideMockProviderHook);
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        hideMockProviderHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(false);
            }
        };

        hideMockGooglePlayServicesHook = new XC_MethodHook() {
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
