package com.oAT.agent.collect.redis;

import com.oAT.agent.common.ReflectUtil;

import java.lang.reflect.Method;

public class RedissonCommandAdapter {
    private final Object target;
    private final Method _getName;

    public RedissonCommandAdapter(Object target) {
        this.target = target;
        try {
            _getName = target.getClass().getMethod("getName");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong " + target.getClass().getName());
        }
    }

    public String getName() {
        Object result = ReflectUtil.invoker(_getName, target);
        return result != null ? result.toString() : "";
    }
}
