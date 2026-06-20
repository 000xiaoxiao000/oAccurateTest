package com.oAT.agent.sandbox.core;

import com.oAT.agent.sandbox.api.ClassMatcher;
import com.oAT.agent.sandbox.api.EventType;
import com.oAT.agent.sandbox.api.MethodMatcher;
import com.oAT.agent.sandbox.api.WatchId;

import java.util.EnumSet;

public class WatchDefinition {
    private final WatchId watchId;
    private final long listenerId;
    private final ClassMatcher classMatcher;
    private final MethodMatcher methodMatcher;
    private final EnumSet<EventType> eventTypes;

    public WatchDefinition(WatchId watchId,
                           long listenerId,
                           ClassMatcher classMatcher,
                           MethodMatcher methodMatcher,
                           EnumSet<EventType> eventTypes) {
        this.watchId = watchId;
        this.listenerId = listenerId;
        this.classMatcher = classMatcher;
        this.methodMatcher = methodMatcher;
        this.eventTypes = eventTypes;
    }

    public WatchId watchId() {
        return watchId;
    }

    public long listenerId() {
        return listenerId;
    }

    public ClassMatcher classMatcher() {
        return classMatcher;
    }

    public MethodMatcher methodMatcher() {
        return methodMatcher;
    }

    public EnumSet<EventType> eventTypes() {
        return eventTypes;
    }
}
