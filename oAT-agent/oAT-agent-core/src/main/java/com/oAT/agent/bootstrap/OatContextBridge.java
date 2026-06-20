package com.oAT.agent.bootstrap;

public interface OatContextBridge {
    Runnable wrap(Runnable runnable);

    java.util.concurrent.Callable wrap(java.util.concurrent.Callable callable);
}
