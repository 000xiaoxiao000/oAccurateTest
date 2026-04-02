package com.oAT.agent.jacoco.data;

import com.oAT.agent.jacoco.instr.InstrSupport;

/**
 * 覆盖率导出场景专用的类名/方法名归一化工具。
 */
final class CoverageNamingSupport {
    private static final String CLINIT_NAME = "<clinit>";

    private CoverageNamingSupport() {
    }

    static String toOwnerQualifiedClassName(String vmClassName) {
        if (vmClassName == null || vmClassName.isEmpty()) {
            return vmClassName;
        }
        int nestedIndex = vmClassName.indexOf('$');
        String ownerClassName = nestedIndex >= 0 ? vmClassName.substring(0, nestedIndex) : vmClassName;
        return ownerClassName.replace('/', '.');
    }

    static String buildMethodMergeKey(String vmClassName, String rawMethodName, String methodDesc) {
        return String.valueOf(vmClassName) + "#" + String.valueOf(rawMethodName) + "#" + String.valueOf(methodDesc);
    }

    static boolean shouldIgnoreMethod(String rawMethodName, String displayMethodName) {
        if (rawMethodName == null || rawMethodName.isEmpty()) {
            return true;
        }
        if (InstrSupport.INITMETHOD_NAME.equals(rawMethodName) || CLINIT_NAME.equals(rawMethodName)) {
            return true;
        }
        if (displayMethodName == null || displayMethodName.isEmpty()) {
            return true;
        }
        return "{...}".equals(displayMethodName) || "static {...}".equals(displayMethodName);
    }
}
