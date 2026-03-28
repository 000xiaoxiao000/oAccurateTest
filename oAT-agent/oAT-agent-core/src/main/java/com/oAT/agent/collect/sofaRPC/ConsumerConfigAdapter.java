package com.oAT.agent.collect.sofaRPC;

import com.oAT.agent.common.ReflectUtil;

import java.lang.reflect.Method;

public class ConsumerConfigAdapter {
    private final Object target;
    private final Method _getConfig;

    public ConsumerConfigAdapter(Object target) {
        this.target = target;
        try{
            _getConfig = target.getClass().getMethod("getConfig");
        }catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong " + target.getClass().getName());
        }
    }

    public String getDirectUrl() {
        Object configObj = ReflectUtil.invoker(_getConfig, target);
        Method getDirectUrl;
        try{
            getDirectUrl = configObj.getClass().getMethod("getDirectUrl");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong " + configObj.getClass().getName());
        }
        return (String) ReflectUtil.invoker(getDirectUrl, configObj);
    }
}
