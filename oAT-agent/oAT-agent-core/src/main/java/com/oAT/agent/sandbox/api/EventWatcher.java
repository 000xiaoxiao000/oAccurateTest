package com.oAT.agent.sandbox.api;

import java.util.EnumSet;

public interface EventWatcher {
    WatchId watch(ClassMatcher classMatcher,
                  MethodMatcher methodMatcher,
                  EnumSet<EventType> eventTypes,
                  EventListener listener);

    void delete(WatchId watchId);
}
