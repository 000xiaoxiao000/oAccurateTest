package com.oAT.agent.collect.sofaRPC;

import com.oAT.agent.common.Assert;
import com.oAT.agent.common.ReflectUtil;

import java.lang.reflect.Method;

public class ResultAdapter {
    Object target;
    private final Method _getErrorMsg;

    public ResultAdapter(Object target) {
        Assert.notNull(target);
        try {
            _getErrorMsg = target.getClass().getMethod("getErrorMsg");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong" + target.getClass().getName());
        }
        this.target = target;
    }

    public Throwable getErrorMsg() {
        return (Throwable) ReflectUtil.invoker(_getErrorMsg, target);
    }
}
