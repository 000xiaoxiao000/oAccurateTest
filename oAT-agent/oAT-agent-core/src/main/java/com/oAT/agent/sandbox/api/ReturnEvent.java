package com.oAT.agent.sandbox.api;

public class ReturnEvent extends SandboxEvent {
    public ReturnEvent(long invokeId, long listenerId, Object returnValue) {
        super(EventType.RETURN, invokeId, listenerId, null, null, null, null, null, null, returnValue, null);
    }
}
