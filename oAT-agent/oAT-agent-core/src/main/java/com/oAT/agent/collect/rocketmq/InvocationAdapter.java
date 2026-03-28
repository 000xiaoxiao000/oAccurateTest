package com.oAT.agent.collect.rocketmq;

import com.oAT.agent.common.ReflectUtil;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.lang.reflect.Method;
import java.util.Map;

public class InvocationAdapter {
    private final static Log logger = LogFactory.getLog(InvocationAdapter.class);
    private final Object target;
    private final Method _putUserProperty;
    private final Method _getProperties;

    public InvocationAdapter(Object target) {
        this.target = target;
        try {
            _putUserProperty = target.getClass().getMethod("putUserProperty", String.class, String.class);
            _getProperties = target.getClass().getMethod("getProperties");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong RocketMQ.RocketMQInvocation");
        }
    }

    public void putUserProperty(String key, String value) {
        try {
            ReflectUtil.invoker(_putUserProperty, target, key, value);
        } catch (Throwable e) {
            logger.error("[Agent-EXCError]putUserProperty error" + StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getProperties() {
        return (Map<String, String>) ReflectUtil.invoker(_getProperties, target);
    }

}
