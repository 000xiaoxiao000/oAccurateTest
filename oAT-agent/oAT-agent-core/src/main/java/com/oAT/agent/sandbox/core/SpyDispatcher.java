package com.oAT.agent.sandbox.core;

import com.oAT.agent.sandbox.spy.OatSpy;

public class SpyDispatcher implements OatSpy.SpyHandler {
    private final ListenerRegistry listenerRegistry;

    public SpyDispatcher(ListenerRegistry listenerRegistry) {
        this.listenerRegistry = listenerRegistry;
    }

    @Override
    public long onBefore(String namespace,
                         long listenerId,
                         ClassLoader loader,
                         String className,
                         String methodName,
                         String descriptor,
                         Object target,
                         Object[] args) {
        return listenerRegistry.onBefore(namespace, listenerId, loader, className, methodName, descriptor, target, args);
    }

    @Override
    public Object onReturn(long listenerId, long invokeId, Object returnValue) {
        return listenerRegistry.onReturn(listenerId, invokeId, returnValue);
    }

    @Override
    public void onThrows(long listenerId, long invokeId, Throwable throwable) {
        listenerRegistry.onThrows(listenerId, invokeId, throwable);
    }
}
