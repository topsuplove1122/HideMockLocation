package com.github.thepiemonster.hidemocklocation;

import java.util.Arrays;
import java.util.HashSet;


public class Common {

    public static final String PACKAGE_NAME = Common.class.getPackage().getName();
    public static final String ACTIVITY_NAME = PACKAGE_NAME + ".MainActivity";

    public static final String GMS_MOCK_KEY = "mockLocation"; // FusedLocationProviderApi.KEY_MOCK_LOCATION

    // Processes/Packages that should always see true 'Allow mock locations' value
    public static final HashSet<String> SYSTEM_WHITELIST = new HashSet<>(Arrays.asList("com.android.settings", "com.sec.android.providers.security"));
}
