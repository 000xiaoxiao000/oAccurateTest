package com.oAT.agent.sandbox.api;

public interface OatModule {
    String id();

    void load(ModuleContext context);

    void active();

    void frozen();

    void unload();
}
