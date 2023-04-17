package com.github.thepiemonster.hidemocklocation;

import android.annotation.SuppressLint;
import android.app.Service;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.InvocationTargetException;
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


    @SuppressLint("PrivateApi")
    private Class<?> loadClassIfExist(XC_LoadPackage.LoadPackageParam lpparam, String name) {
        try {
            return lpparam.classLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null) {
            return;
        }

        if (lpparam.packageName.equals("android")) {
////            // First way
//            Class<?> GnssNativeCls = loadClassIfExist(lpparam, "com.android.server.location.gnss.hal.GnssNative");
//            if (GnssNativeCls == null) {
//                // Android < 12
//                GnssNativeCls = loadClassIfExist(lpparam, "com.android.server.location.gnss.GnssNative");
//            }
//            if (GnssNativeCls != null) {
//                Method GnssNativeCls_isSupported = XposedHelpers.findMethodExactIfExists(GnssNativeCls, "isSupported");
//                if (GnssNativeCls_isSupported != null) {
//                    XposedBridge.hookMethod(GnssNativeCls_isSupported, new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam param) {
//                            param.setResult(false);
//                        }
//                    });
//                }
//            }

            // Second way
            Class<?> GnssLocationProviderCls = loadClassIfExist(lpparam, "com.android.server.location.gnss.GnssLocationProvider");
            if (GnssLocationProviderCls == null) {
                // Android < 12
                GnssLocationProviderCls = loadClassIfExist(lpparam, "com.android.server.location.GnssLocationProvider");
            }

            if (GnssLocationProviderCls != null) {
                Method handleRequestLocation = XposedHelpers.findMethodExactIfExists(GnssLocationProviderCls, "handleRequestLocation", boolean.class, boolean.class);
                Method handleReportLocation = XposedHelpers.findMethodExactIfExists(GnssLocationProviderCls, "handleReportLocation", boolean.class, Location.class);

                if (handleRequestLocation != null) {
                    XposedBridge.hookMethod(handleRequestLocation, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });
                }
                if (handleReportLocation != null) {
                    XposedBridge.hookMethod(handleReportLocation, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });
                }
            }
        } else {
            // GPS Joystick (Samsung devices) bug fix
            String packageName = lpparam.packageName;
            Class<?> joystick_MapOverlayService = loadClassIfExist(lpparam, packageName + ".service.MapOverlayService");
            if (joystick_MapOverlayService != null) {
                Method joystick_MapOverlayService_onDestroy = XposedHelpers.findMethodExactIfExists(
                        joystick_MapOverlayService,
                        "onDestroy"
                );
                if (joystick_MapOverlayService_onDestroy != null) {
                    XposedBridge.hookMethod(joystick_MapOverlayService_onDestroy, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Service thisObject = (Service) param.thisObject;
                            Method _internalDestroyMethod = XposedHelpers.findMethodExactIfExists(
                                    thisObject.getClass(),
                                    "p"
                            );
                            if (_internalDestroyMethod != null) {
                                try {
                                    _internalDestroyMethod.invoke(thisObject);
                                    param.setResult(null);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    // do nothing.
                                }
                            } else {
                                XposedBridge.log("Failed to call internal destroy method ( Joystick )");
                            }
                        }
                    });
                }
            } else {
                handleLoadPackageForApps(lpparam);
            }
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
