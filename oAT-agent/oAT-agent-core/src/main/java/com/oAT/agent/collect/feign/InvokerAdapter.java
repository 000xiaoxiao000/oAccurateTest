package com.oAT.agent.collect.feign;

import com.oAT.agent.common.Assert;
import com.oAT.agent.common.ReflectUtil;

import java.lang.reflect.Method;

public class InvokerAdapter {
    private final Object target;
    private final Method _getFeignTarget;
    private final Method _getMethod;
    private final Method _getURL;
    private final Method _getBody;

    public InvokerAdapter(Object target) {
        this.target = target;
        Assert.notNull(target);
        try {
            _getFeignTarget = target.getClass().getMethod("feignTarget");
            _getMethod = target.getClass().getMethod("method");
            _getURL = target.getClass().getMethod("url");
            _getBody = target.getClass().getMethod("body");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong FeignTarget");
        }
    }

    public String getFeignTargetName() {
        try {
            Object targetInvoke = ReflectUtil.invoker(_getFeignTarget, target);
            if (targetInvoke == null) {
                return "";
            }
            Class<?> aClass = targetInvoke.getClass();
            Method name = aClass.getMethod("name");
            Object result = ReflectUtil.invoker(name, targetInvoke);
            return result != null ? result.toString() : "";
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("error: " + e.getMessage() + ". probable cause the target is not " +
                    "belong FeignTarget");
        }
    }

    public String getMethod() {
        Object result = ReflectUtil.invoker(_getMethod, target);
        return result != null ? result.toString() : "";
    }

    public String getURL() {
        Object result = ReflectUtil.invoker(_getURL, target);
        return result != null ? result.toString() : "";
    }


    public byte[] getBody() {
        return (byte[]) ReflectUtil.invoker(_getBody, target);
    }
}
