package com.oAT.agent.sandbox.api;

public class ThrowsEvent extends SandboxEvent {
    public ThrowsEvent(long invokeId, long listenerId, Throwable throwable) {
        super(EventType.THROWS, invokeId, listenerId, null, null, null, null, null, null, null, throwable);
    }
}
