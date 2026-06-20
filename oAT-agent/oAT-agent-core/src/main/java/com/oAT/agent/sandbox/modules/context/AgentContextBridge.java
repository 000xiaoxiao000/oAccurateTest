package com.oAT.agent.sandbox.modules.context;

import com.oAT.agent.context.AgentContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class AgentContextBridge {
    public Runnable wrap(Runnable runnable) {
        return AgentContext.wrap(runnable);
    }

    public java.util.concurrent.Callable wrap(java.util.concurrent.Callable callable) {
        return AgentContext.wrap(callable);
    }

    public Collection wrap(Collection tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return tasks;
        }
        ArrayList wrapped = new ArrayList(tasks.size());
        Iterator iterator = tasks.iterator();
        while (iterator.hasNext()) {
            Object task = iterator.next();
            if (task instanceof java.util.concurrent.Callable) {
                wrapped.add(AgentContext.wrap((java.util.concurrent.Callable) task));
            } else {
                wrapped.add(task);
            }
        }
        return wrapped;
    }

    public void release(Object task) {
        if (task instanceof Collection) {
            Iterator iterator = ((Collection) task).iterator();
            while (iterator.hasNext()) {
                AgentContext.cancelAsyncTask(iterator.next());
            }
            return;
        }
        AgentContext.cancelAsyncTask(task);
    }
}
