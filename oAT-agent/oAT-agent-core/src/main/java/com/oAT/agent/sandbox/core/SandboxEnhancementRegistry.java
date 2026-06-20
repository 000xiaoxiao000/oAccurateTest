package com.oAT.agent.sandbox.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SandboxEnhancementRegistry {
    private static final int MAX_METHODS_PER_MODULE = 20;

    private final Map<String, ModuleEnhancement> modules = new LinkedHashMap<String, ModuleEnhancement>();

    public synchronized void record(String moduleId, String className, String methodName, String descriptor) {
        if (moduleId == null || moduleId.length() == 0 || className == null || className.length() == 0) {
            return;
        }
        ModuleEnhancement enhancement = modules.get(moduleId);
        if (enhancement == null) {
            enhancement = new ModuleEnhancement(moduleId);
            modules.put(moduleId, enhancement);
        }
        enhancement.record(className, methodName, descriptor);
    }

    public synchronized void recordClass(String moduleId, String className) {
        if (moduleId == null || moduleId.length() == 0 || className == null || className.length() == 0) {
            return;
        }
        ModuleEnhancement enhancement = modules.get(moduleId);
        if (enhancement == null) {
            enhancement = new ModuleEnhancement(moduleId);
            modules.put(moduleId, enhancement);
        }
        enhancement.recordClass(className);
    }

    public synchronized Map<String, Map<String, Object>> summaries() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
        for (Map.Entry<String, ModuleEnhancement> entry : modules.entrySet()) {
            result.put(entry.getKey(), entry.getValue().summary());
        }
        return Collections.unmodifiableMap(result);
    }

    private static class ModuleEnhancement {
        private final String moduleId;
        private final Map<String, Boolean> classes = new LinkedHashMap<String, Boolean>();
        private final Map<String, Boolean> methods = new LinkedHashMap<String, Boolean>();
        private long lastEnhancedAt;

        private ModuleEnhancement(String moduleId) {
            this.moduleId = moduleId;
        }

        private void record(String className, String methodName, String descriptor) {
            classes.put(className, Boolean.TRUE);
            String methodKey = className + "#" + methodName + descriptor;
            methods.put(methodKey, Boolean.TRUE);
            lastEnhancedAt = System.currentTimeMillis();
        }

        private void recordClass(String className) {
            classes.put(className, Boolean.TRUE);
            lastEnhancedAt = System.currentTimeMillis();
        }

        private Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("moduleId", moduleId);
            result.put("enhancedClassCount", Integer.valueOf(classes.size()));
            result.put("enhancedMethodCount", Integer.valueOf(methods.size()));
            result.put("lastEnhancedAt", Long.valueOf(lastEnhancedAt));
            List<String> samples = new ArrayList<String>();
            int count = 0;
            for (String method : methods.keySet()) {
                if (count >= MAX_METHODS_PER_MODULE) {
                    break;
                }
                samples.add(method);
                count++;
            }
            result.put("sampleMethods", samples);
            return result;
        }
    }
}
