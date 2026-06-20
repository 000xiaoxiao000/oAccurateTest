package com.oAT.agent.collect.rabbitmq;

import com.oAT.agent.common.ReflectUtil;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class InvocationAdapter {
    private final static Log logger = LogFactory.getLog(InvocationAdapter.class);
    private final Object target;
    private final Field _headers;
    private final Method _getHeaders;

    public InvocationAdapter(Object target) {
        this.target = target;
        try {
            _headers = target.getClass().getDeclaredField("headers");
            _getHeaders = target.getClass().getMethod("getHeaders");
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong RabbitMQ.RabbitMQInvocation");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong RabbitMQ.RabbitMQInvocation");
        }
    }

    public void addHeader(String key, String value) {
        boolean old = _headers.isAccessible();
        try {
            _headers.setAccessible(true);
            Map<String, Object> headers = (Map<String, Object>) _headers.get(target);
            if (headers.getClass().getName().contains("Unmodifiable")) {
                headers = new HashMap(headers);
                _headers.set(target, headers);
            }
            headers.put(key, value);
        } catch (Throwable e) {
            logger.error("[Agent-EXCError]addHeader error. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        } finally {
            _headers.setAccessible(old);
        }
    }

    public Map<String, Object> getHeaders() {
        return (Map<String, Object>) ReflectUtil.invoker(_getHeaders, target);
    }
}
