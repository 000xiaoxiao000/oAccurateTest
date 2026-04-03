package com.oAT.web.common;

public final class CoverageMethodKeyUtil {

    private CoverageMethodKeyUtil() {
    }

    public static String buildMethodKey(String methodName, String methodDesc) {
        return String.valueOf(methodName) + "#" + String.valueOf(methodDesc);
    }
}
