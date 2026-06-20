package com.oAT.agent.sandbox.core;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.sandbox.api.BeforeEvent;
import com.oAT.agent.sandbox.api.EventListener;
import com.oAT.agent.sandbox.api.ReturnEvent;
import com.oAT.agent.sandbox.api.ThrowsEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class ListenerRegistry {
    private static final Log logger = LogFactory.getLog(ListenerRegistry.class);

    private final AtomicLong listenerIdGenerator = new AtomicLong();
    private final AtomicLong invokeIdGenerator = new AtomicLong();
    private final ConcurrentMap<Long, EventListener> listeners = new ConcurrentHashMap<Long, EventListener>();

    public long register(EventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        long id = listenerIdGenerator.incrementAndGet();
        listeners.put(id, listener);
        return id;
    }

    public void unregister(long listenerId) {
        listeners.remove(listenerId);
    }

    public long onBefore(String namespace,
                         long listenerId,
                         ClassLoader loader,
                         String className,
                         String methodName,
                         String descriptor,
                         Object target,
                         Object[] args) {
        EventListener listener = listeners.get(listenerId);
        if (listener == null) {
            return 0L;
        }
        long invokeId = invokeIdGenerator.incrementAndGet();
        dispatch(listener, new BeforeEvent(invokeId, listenerId, loader, className, methodName, descriptor, target, args));
        return invokeId;
    }

    public Object onReturn(long listenerId, long invokeId, Object returnValue) {
        EventListener listener = listeners.get(listenerId);
        if (listener != null) {
            Object replacement = dispatch(listener, new ReturnEvent(invokeId, listenerId, returnValue));
            return replacement == null ? returnValue : replacement;
        }
        return returnValue;
    }

    public void onThrows(long listenerId, long invokeId, Throwable throwable) {
        EventListener listener = listeners.get(listenerId);
        if (listener != null) {
            dispatch(listener, new ThrowsEvent(invokeId, listenerId, throwable));
        }
    }

    private Object dispatch(EventListener listener, com.oAT.agent.sandbox.api.SandboxEvent event) {
        try {
            return listener.onEvent(event);
        } catch (Throwable t) {
            logger.error("[Sandbox] listener dispatch failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }
}
