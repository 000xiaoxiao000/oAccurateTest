package com.oAT.agent.sandbox.core;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.sandbox.api.ModuleState;
import com.oAT.agent.sandbox.api.OatModule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleManager {
    private static final Log logger = LogFactory.getLog(ModuleManager.class);

    private final Map<String, ModuleHolder> modules = new LinkedHashMap<String, ModuleHolder>();

    public synchronized void register(OatModule module, SandboxContext context) {
        if (module == null) {
            return;
        }
        String id = module.id();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("module id must not be blank");
        }
        if (modules.containsKey(id)) {
            throw new IllegalArgumentException("duplicate module id: " + id);
        }
        ModuleHolder holder = new ModuleHolder(module);
        modules.put(id, holder);
        try {
            module.load(context);
            holder.state = ModuleState.LOADED;
            module.active();
            holder.state = ModuleState.ACTIVE;
            logger.info("[Sandbox] module active: " + id);
        } catch (Throwable t) {
            holder.state = ModuleState.ERROR;
            holder.lastError = StackTraceFormatter.formatExceptionWithAgentMark(t);
            logger.error("[Sandbox] module start failed: " + id + " " + holder.lastError);
        }
    }

    public synchronized Map<String, ModuleState> states() {
        Map<String, ModuleState> states = new LinkedHashMap<String, ModuleState>();
        for (Map.Entry<String, ModuleHolder> entry : modules.entrySet()) {
            states.put(entry.getKey(), entry.getValue().state);
        }
        return Collections.unmodifiableMap(states);
    }

    public synchronized Map<String, String> stateNames() {
        Map<String, String> states = new LinkedHashMap<String, String>();
        for (Map.Entry<String, ModuleHolder> entry : modules.entrySet()) {
            ModuleState state = entry.getValue().state;
            states.put(entry.getKey(), state == null ? "" : state.name());
        }
        return Collections.unmodifiableMap(states);
    }

    public synchronized void unloadAll() {
        for (Map.Entry<String, ModuleHolder> entry : modules.entrySet()) {
            ModuleHolder holder = entry.getValue();
            try {
                holder.module.frozen();
                holder.state = ModuleState.FROZEN;
                holder.module.unload();
                holder.state = ModuleState.UNLOADED;
                logger.info("[Sandbox] module unloaded: " + entry.getKey());
            } catch (Throwable t) {
                holder.state = ModuleState.ERROR;
                holder.lastError = StackTraceFormatter.formatExceptionWithAgentMark(t);
                logger.error("[Sandbox] module unload failed: " + entry.getKey() + " " + holder.lastError);
            }
        }
    }

    private static class ModuleHolder {
        private final OatModule module;
        private ModuleState state = ModuleState.UNLOADED;
        private String lastError;

        private ModuleHolder(OatModule module) {
            this.module = module;
        }
    }
}
