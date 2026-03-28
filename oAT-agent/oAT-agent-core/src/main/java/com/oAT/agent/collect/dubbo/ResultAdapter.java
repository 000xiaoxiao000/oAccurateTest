package com.oAT.agent.collect.dubbo;

import com.oAT.agent.common.Assert;
import com.oAT.agent.common.ReflectUtil;

import java.lang.reflect.Method;

public class ResultAdapter {
    Object target;
    private final Method _getException;

    public ResultAdapter(Object target) {
        Assert.notNull(target);
        try {
            _getException = target.getClass().getMethod("getException");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong com.apache.dubbo.rpc.RpcInvocation");
        }
        this.target = target;
    }

    public Throwable getException() {
        return (Throwable) ReflectUtil.invoker(_getException, target);
    }
}
