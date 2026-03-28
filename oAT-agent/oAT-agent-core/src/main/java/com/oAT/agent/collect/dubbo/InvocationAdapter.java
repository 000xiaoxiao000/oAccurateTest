package com.oAT.agent.collect.dubbo;

import com.oAT.agent.common.ReflectUtil;

import java.lang.reflect.Method;

/**
 * 适配目标：com.alibaba.dubbo.rpc.Invocation
 */
public class InvocationAdapter {
    private final Method _getArguments;
    private final Method _getInvoker;
    private final Method _getMethodName;
    private final Method _setAttachment;
    private final Method _getAttachment;
    private final Object target;

    public InvocationAdapter(Object target) {
        this.target = target;
        try {
            _getMethodName = target.getClass().getMethod("getMethodName");
            _getArguments = target.getClass().getMethod("getArguments");
            _getInvoker = target.getClass().getMethod("getInvoker");
            _setAttachment = target.getClass().getMethod("setAttachment", String.class, String.class);
            _getAttachment = target.getClass().getMethod("getAttachment", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() +". "
                    + "probable cause the target is not belong com.apache.dubbo.rpc.RpcInvocation");
        }
    }

    public String getMethodName() {
        return (String) ReflectUtil.invoker(_getMethodName, target);
    }

    public Object getInvoker() {
        return ReflectUtil.invoker(_getInvoker, target);
    }

    public Object[] getArguments() {
        return (Object[]) ReflectUtil.invoker(_getArguments, target);
    }

    public void setAttachment(String key, String value) {
        ReflectUtil.invoker(_setAttachment, target, key, value);
    }

    public String getAttachment(String key) {
        return (String) ReflectUtil.invoker(_getAttachment, target, key);
    }
}
