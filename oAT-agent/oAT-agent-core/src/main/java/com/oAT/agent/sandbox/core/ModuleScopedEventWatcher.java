package com.oAT.agent.sandbox.core;

import com.oAT.agent.sandbox.api.ClassMatcher;
import com.oAT.agent.sandbox.api.EventListener;
import com.oAT.agent.sandbox.api.EventType;
import com.oAT.agent.sandbox.api.EventWatcher;
import com.oAT.agent.sandbox.api.MethodMatcher;
import com.oAT.agent.sandbox.api.WatchId;

import java.util.EnumSet;

public class ModuleScopedEventWatcher implements EventWatcher {
    private final DefaultEventWatcher delegate;
    private final String moduleId;

    public ModuleScopedEventWatcher(DefaultEventWatcher delegate, String moduleId) {
        this.delegate = delegate;
        this.moduleId = moduleId;
    }

    @Override
    public WatchId watch(ClassMatcher classMatcher,
                         MethodMatcher methodMatcher,
                         EnumSet<EventType> eventTypes,
                         EventListener listener) {
        return delegate.watch(moduleId, classMatcher, methodMatcher, eventTypes, listener);
    }

    @Override
    public void delete(WatchId watchId) {
        delegate.delete(watchId);
    }
}

