package com.oAT.agent.sandbox.core;

import com.oAT.agent.sandbox.api.ClassMatcher;
import com.oAT.agent.sandbox.api.EventListener;
import com.oAT.agent.sandbox.api.EventType;
import com.oAT.agent.sandbox.api.EventWatcher;
import com.oAT.agent.sandbox.api.MethodMatcher;
import com.oAT.agent.sandbox.api.WatchId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultEventWatcher implements EventWatcher {
    private final AtomicLong watchIdGenerator = new AtomicLong();
    private final ListenerRegistry listenerRegistry;
    private final Map<Long, WatchDefinition> watches = new LinkedHashMap<Long, WatchDefinition>();

    public DefaultEventWatcher(ListenerRegistry listenerRegistry) {
        this.listenerRegistry = listenerRegistry;
    }

    @Override
    public synchronized WatchId watch(ClassMatcher classMatcher,
                                      MethodMatcher methodMatcher,
                                      EnumSet<EventType> eventTypes,
                                      EventListener listener) {
        if (classMatcher == null) {
            throw new IllegalArgumentException("classMatcher must not be null");
        }
        if (methodMatcher == null) {
            throw new IllegalArgumentException("methodMatcher must not be null");
        }
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("eventTypes must not be empty");
        }
        long listenerId = listenerRegistry.register(listener);
        WatchId watchId = new WatchId(watchIdGenerator.incrementAndGet());
        watches.put(watchId.value(), new WatchDefinition(watchId, listenerId, classMatcher, methodMatcher,
                EnumSet.copyOf(eventTypes)));
        return watchId;
    }

    @Override
    public synchronized void delete(WatchId watchId) {
        if (watchId == null) {
            return;
        }
        WatchDefinition removed = watches.remove(watchId.value());
        if (removed != null) {
            listenerRegistry.unregister(removed.listenerId());
        }
    }

    public synchronized List<WatchDefinition> snapshot() {
        return Collections.unmodifiableList(new ArrayList<WatchDefinition>(watches.values()));
    }
}
