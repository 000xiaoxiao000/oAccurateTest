package com.oAT.agent.sandbox.api;

public final class WatchId {
    private final long value;

    public WatchId(long value) {
        this.value = value;
    }

    public long value() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
