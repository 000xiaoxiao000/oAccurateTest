package com.oAT.agent.collect.sofaRPC;

import com.oAT.agent.common.ReflectUtil;

import java.lang.reflect.Method;

public class SofaRequestAdapter {
    private final Object target;
    private final Method _getMethodName;
    private final Method _getMethodArgs;
    private final Method _getTargetServiceUniqueName;
    private final Method _getInterfaceName;
    private final Method _addRequestProp;
    private final Method _getRequestProp;
    private final Method _getInvokeType;

    public SofaRequestAdapter(Object target) {
        this.target = target;
        try {
            _getMethodName = target.getClass().getMethod("getMethodName");
            _getMethodArgs = target.getClass().getMethod("getMethodArgs");
            _getTargetServiceUniqueName = target.getClass().getMethod("getTargetServiceUniqueName");
            _getInterfaceName = target.getClass().getMethod("getInterfaceName");
            _addRequestProp = target.getClass().getMethod("addRequestProp", String.class, Object.class);
            _getRequestProp = target.getClass().getMethod("getRequestProp", String.class);
            _getInvokeType = target.getClass().getMethod("getInvokeType");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong " + target.getClass().getName());
        }
    }

    public String getMethodName() {
        return (String) ReflectUtil.invoker(_getMethodName, target);
    }

    public Object[] getMethodArgs() {
        return (Object[]) ReflectUtil.invoker(_getMethodArgs, target);
    }

    public String getTargetServiceUniqueName() {
        return (String) ReflectUtil.invoker(_getTargetServiceUniqueName, target);
    }

    public String getInterfaceName() {
        return (String) ReflectUtil.invoker(_getInterfaceName, target);
    }

    public void addRequestProp(String key, String value) {
        ReflectUtil.invoker(_addRequestProp, target, key, value);
    }

    public String getRequestProp(String key) {
        return (String) ReflectUtil.invoker(_getRequestProp, target, key);
    }

    public String getInvokeType() {
        return (String) ReflectUtil.invoker(_getInvokeType, target);
    }
}
