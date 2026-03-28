package com.oAT.web.service.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CoverageComparisonVo implements Serializable {
    private int addedCount;
    private int stableCount;
    private int decreasedCount;

    private List<MethodDiff> addedMethods = new ArrayList<>();
    private List<MethodDiff> stableMethods = new ArrayList<>();
    private List<MethodDiff> decreasedMethods = new ArrayList<>();

    public static class MethodDiff implements Serializable {
        private String className;
        private String methodName;
        private String methodDesc;

        public MethodDiff(String className, String methodName, String methodDesc) {
            this.className = className;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
        }

        public String getClassName() { return className; }
        public String getMethodName() { return methodName; }
        public String getMethodDesc() { return methodDesc; }
    }

    public int getAddedCount() { return addedCount; }
    public void setAddedCount(int addedCount) { this.addedCount = addedCount; }
    public int getStableCount() { return stableCount; }
    public void setStableCount(int stableCount) { this.stableCount = stableCount; }
    public int getDecreasedCount() { return decreasedCount; }
    public void setDecreasedCount(int decreasedCount) { this.decreasedCount = decreasedCount; }
    public List<MethodDiff> getAddedMethods() { return addedMethods; }
    public void setAddedMethods(List<MethodDiff> addedMethods) { this.addedMethods = addedMethods; }
    public List<MethodDiff> getStableMethods() { return stableMethods; }
    public void setStableMethods(List<MethodDiff> stableMethods) { this.stableMethods = stableMethods; }
    public List<MethodDiff> getDecreasedMethods() { return decreasedMethods; }
    public void setDecreasedMethods(List<MethodDiff> decreasedMethods) { this.decreasedMethods = decreasedMethods; }
}

