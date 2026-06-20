package com.oAT.agent.sandbox.api;

public class BeforeEvent extends SandboxEvent {
    public BeforeEvent(long invokeId,
                       long listenerId,
                       ClassLoader loader,
                       String className,
                       String methodName,
                       String descriptor,
                       Object target,
                       Object[] args) {
        super(EventType.BEFORE, invokeId, listenerId, loader, className, methodName, descriptor, target, args, null, null);
    }
}
