package com.oAT.agent.collect.kafka;

import com.oAT.agent.common.ReflectUtil;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InvocationProducerAdapter {
    private final static Log logger = LogFactory.getLog(ReflectUtil.class);
    private final Object target;
    private final Field _headers;

    public InvocationProducerAdapter(Object target) {
        this.target = target;
        try {
            _headers = target.getClass().getDeclaredField("headers");
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong KafkaMQ");
        }
    }

    public void addHeader(String key, byte[]... values) {
        boolean old = _headers.isAccessible();
        try {
            _headers.setAccessible(true);
            Object headers = _headers.get(target);
            Method addMethod = headers.getClass().getMethod("add", String.class, byte[].class);
            for (byte[] value : values) {
                addMethod.invoke(headers, key, value);
            }
        } catch (IllegalAccessException e) {
            logger.error("[Agent-EXCError]addHeader error. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        } catch (NoSuchMethodException e) {
            logger.error("[Agent-EXCError]addHeader error. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        } catch (InvocationTargetException e) {
            logger.error("[Agent-EXCError]addHeader error. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        } finally {
            _headers.setAccessible(old);
        }
    }
}
