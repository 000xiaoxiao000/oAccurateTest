package com.oAT.agent.collect.dubbo;

import com.oAT.agent.common.Assert;
import com.oAT.agent.common.ReflectUtil;

import java.lang.reflect.Method;

public class InvokerAdapter {
    private final Method _getInterface;
    private final Method _getUrl;
    private final Object target;

    public InvokerAdapter(Object target) {
        this.target = target;
        Assert.notNull(target);
        try {
            _getInterface = target.getClass().getMethod("getInterface");
            _getUrl = target.getClass().getMethod("getUrl");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong com.apache.dubbo.rpc.Invoker");
        }
    }

    public Class getInterface() {
        return (Class) ReflectUtil.invoker(_getInterface, target);
    }

    public String getUrl() {
        return ReflectUtil.invoker(_getUrl, target).toString();
    }

}
